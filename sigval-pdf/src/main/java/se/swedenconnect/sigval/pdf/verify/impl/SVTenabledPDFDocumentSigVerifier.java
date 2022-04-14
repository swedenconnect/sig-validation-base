package se.swedenconnect.sigval.pdf.verify.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;
import se.idsec.signservice.security.certificate.CertificateValidationResult;
import se.idsec.signservice.security.certificate.CertificateValidator;
import se.idsec.signservice.security.certificate.impl.DefaultCertificateValidationResult;
import se.idsec.signservice.security.sign.SignatureValidationResult;
import se.swedenconnect.sigval.commons.algorithms.JWSAlgorithmRegistry;
import se.swedenconnect.sigval.commons.data.PubKeyParams;
import se.swedenconnect.sigval.commons.data.SigValIdentifiers;
import se.swedenconnect.sigval.commons.data.SignedDocumentValidationResult;
import se.swedenconnect.sigval.commons.data.TimeValidationResult;
import se.swedenconnect.sigval.commons.utils.GeneralCMSUtils;
import se.swedenconnect.sigval.commons.utils.SVAUtils;
import se.swedenconnect.sigval.pdf.data.ExtendedPdfSigValResult;
import se.swedenconnect.sigval.pdf.pdfstruct.PDFSignatureContext;
import se.swedenconnect.sigval.pdf.pdfstruct.PDFSignatureContextFactory;
import se.swedenconnect.sigval.pdf.svt.PDFSVTValidator;
import se.swedenconnect.sigval.pdf.timestamp.PDFDocTimeStamp;
import se.swedenconnect.sigval.pdf.utils.CMSVerifyUtils;
import se.swedenconnect.sigval.pdf.utils.PDFSVAUtils;
import se.swedenconnect.sigval.pdf.verify.ExtendedPDFSignatureValidator;
import se.swedenconnect.sigval.pdf.verify.PDFSingleSignatureValidator;
import se.swedenconnect.sigval.svt.algorithms.SVTAlgoRegistry;
import se.swedenconnect.sigval.svt.claims.PolicyValidationClaims;
import se.swedenconnect.sigval.svt.claims.SignatureClaims;
import se.swedenconnect.sigval.svt.claims.TimeValidationClaims;
import se.swedenconnect.sigval.svt.claims.ValidationConclusion;
import se.swedenconnect.sigval.svt.validation.SignatureSVTValidationResult;

/**
 * This class provides the functionality to validate signatures on a PDF where the signature validation process is enhanced with validation
 * based on SVA (Signature Validation Assertions). The latest valid SVA that can be verified given the provided trust validation resources is selected.
 * Signatures covered by this SVA is validated based on SVA. Any other signatures are validated through traditional signature validation methods.
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class SVTenabledPDFDocumentSigVerifier implements ExtendedPDFSignatureValidator {

  public static Logger LOG = Logger.getLogger(SVTenabledPDFDocumentSigVerifier.class.getName());
  /** SVT token validator **/
  private final PDFSVTValidator pdfsvtValidator;
  /** Signature verifier for signatures not supported by SVT. This verifier is also performing validation of signature timestamps **/
  private final PDFSingleSignatureValidator pdfSingleSignatureValidator;
  private final PDFSignatureContextFactory pdfSignatureContextFactory;

  /**
   * Constructor if no SVT validation is supported
   *
   * @param pdfSingleSignatureValidator The verifier used to verify signatures not supported by SVA
   * @param pdfSignatureContextFactory factory for creating an instance of signature context for the validated document
   */
  public SVTenabledPDFDocumentSigVerifier(PDFSingleSignatureValidator pdfSingleSignatureValidator, PDFSignatureContextFactory pdfSignatureContextFactory) {
    this.pdfSingleSignatureValidator = pdfSingleSignatureValidator;
    this.pdfSignatureContextFactory = pdfSignatureContextFactory;
    this.pdfsvtValidator = null;
  }

  /**
   * Constructor
   *
   * @param pdfSingleSignatureValidator The verifier used to verify signatures not supported by SVA
   * @param pdfsvtValidator      Certificate verifier for the certificate used to sign SVA tokens
   * @param pdfSignatureContextFactory factory for creating an instance of signature context for the validated document
   */
  public SVTenabledPDFDocumentSigVerifier(PDFSingleSignatureValidator pdfSingleSignatureValidator, PDFSVTValidator pdfsvtValidator, PDFSignatureContextFactory pdfSignatureContextFactory) {
    this.pdfSingleSignatureValidator = pdfSingleSignatureValidator;
    this.pdfsvtValidator = pdfsvtValidator;
    this.pdfSignatureContextFactory = pdfSignatureContextFactory;
  }

  /**
   * Verifies the signatures of a PDF document. Validation based on SVT is given preference over traditional signature validation.
   *
   * @param pdfDoc signed PDF document to verify
   * @return Validation result from PDF verification
   * @throws SignatureException on error
   */
  public List<SignatureValidationResult> validate(File pdfDoc) throws SignatureException {
    byte[] docBytes = null;
    try {
      docBytes = IOUtils.toByteArray(new FileInputStream(pdfDoc));
    }
    catch (IOException ex) {
      throw new SignatureException("Unable to read signed file", ex);
    }
    return validate(docBytes);
  }

  /**
   * Verifies the signatures of a PDF document. Validation based on SVA is given preference over traditional signature validation.
   *
   * @param pdfDocBytes signed PDF document to verify
   * @return Validation result from PDF verification
   * @throws SignatureException on error
   */
  @Override public List<SignatureValidationResult> validate(byte[] pdfDocBytes) throws SignatureException {
    try {
      PDFSignatureContext signatureContext = pdfSignatureContextFactory.getPdfSignatureContext(pdfDocBytes);
      List<PDSignature> allSignatureList = signatureContext.getSignatures();
      List<PDSignature> docTsSigList = new ArrayList<>();
      List<PDSignature> signatureList = new ArrayList<>();

      for (PDSignature signature : allSignatureList) {
        String type = PDFSVAUtils.getSignatureType(signature, signature.getContents(pdfDocBytes));
        switch (type) {
        case PDFSVAUtils.SVT_TYPE:
        case PDFSVAUtils.DOC_TIMESTAMP_TYPE:
          docTsSigList.add(signature);
          break;
        case PDFSVAUtils.SIGNATURE_TYPE:
          signatureList.add(signature);
        }
      }

      // Create empty result list
      List<SignatureValidationResult> sigVerifyResultList = new ArrayList<>();
      // This list starts empty. It is only filled with objects if there is a signature that is validated without SVT.
      List<PDFDocTimeStamp> docTimeStampList = new ArrayList<>();
      boolean docTsVerified = false;
      // Obtain any SVT validation results from a present SVT validator
      List<SignatureSVTValidationResult> svtValidationResults = pdfsvtValidator == null ? null : pdfsvtValidator.validate(pdfDocBytes);

      for (PDSignature signature : signatureList) {
        SignatureSVTValidationResult svtValResult = null;
          svtValResult = getMatchingSvtValidation(PDFSVAUtils.getSignatureValueBytes(signature, pdfDocBytes), svtValidationResults);
        if (svtValResult == null) {
          // This signature is not covered by a valid SVT. Perform normal signature verification
          try {
            //Get verified documentTimestamps if not previously loaded
            if (!docTsVerified) {
              docTimeStampList = pdfSingleSignatureValidator.verifyDocumentTimestamps(docTsSigList, pdfDocBytes);
              docTsVerified = true;
            }

            SignatureValidationResult directVerifyResult = pdfSingleSignatureValidator.verifySignature(signature, pdfDocBytes, docTimeStampList,
              signatureContext);
            sigVerifyResultList.add(directVerifyResult);
          }
          catch (Exception e) {
            LOG.warning("Error parsing the PDF signature: " + e.getMessage());
            sigVerifyResultList.add(getErrorResult(signature, e.getMessage()));
          }
        }
        else {
          // There is SVT validation results. Use them.
          sigVerifyResultList.add(compliePDFSigValResultsFromSvtValidation(svtValResult, signature, pdfDocBytes, signatureContext));
        }
      }
      return sigVerifyResultList;
    }
    catch (Exception ex) {
      throw new SignatureException("Error validating signatures on PDF document", ex);
    }
  }

  /** {@inheritDoc} */
  @Override public boolean isSigned(byte[] document) throws IllegalArgumentException {
    PDDocument pdfDocument = null;
    try {
      pdfDocument = PDDocument.load(document);
      return !pdfDocument.getSignatureDictionaries().isEmpty();
    }
    catch (IOException e) {
      throw new IllegalArgumentException("Invalid document", e);
    }
    finally {
      try {
        if (pdfDocument != null) {
          pdfDocument.close();
        }
      }
      catch (IOException e) {
      }
    }
  }

  /**
   * This implementation allways perform PKIX validation and returns an empty list for this function
   *
   * @return empty list
   */
  @Override public List<X509Certificate> getRequiredSignerCertificates() {
    return new ArrayList<>();
  }

  /** {@inheritDoc} */
  @Override public CertificateValidator getCertificateValidator() {
    return pdfSingleSignatureValidator.getCertificateValidator();
  }

  /**
   * Use the results obtained from SVT validation to produce general signature validation result as if the signature was validated using
   * complete validation.
   *
   * @param svtValResult     results from SVT validation
   * @param signature        the signature being validated
   * @param pdfDocBytes      the bytes of the PDF document
   * @param signatureContext the context of the signature in the PDF document
   * @return {@link ExtendedPdfSigValResult} signature results
   */
  private ExtendedPdfSigValResult compliePDFSigValResultsFromSvtValidation(SignatureSVTValidationResult svtValResult,
    PDSignature signature, byte[] pdfDocBytes, PDFSignatureContext signatureContext) {
    ExtendedPdfSigValResult cmsSVResult = new ExtendedPdfSigValResult();
    cmsSVResult.setPdfSignature(signature);

    try {
      byte[] sigBytes = signature.getContents(pdfDocBytes);
      cmsSVResult.setSignedData(sigBytes);

      //Reaching this point means that the signature is valid and verified through the SVA.
      SignedData signedData = SVAUtils.getSignedDataFromSignature(sigBytes);
      cmsSVResult.setEtsiAdes(signature.getSubFilter().equalsIgnoreCase(PDFSVAUtils.CADES_SIG_SUBFILETER_LC));
      cmsSVResult.setInvalidSignCert(false);
      cmsSVResult.setClaimedSigningTime(PDFSVAUtils.getClaimedSigningTime(signature.getSignDate(), signedData));
      cmsSVResult.setCoversDocument(signatureContext.isCoversWholeDocument(signature));
      byte[] signedDocumentBytes = null;
      try {
        signedDocumentBytes = signatureContext.getSignedDocument(signature);
      } 
      catch (Exception ex){
        log.warn("Error extracting the document version signed by this signature: {}", ex.getMessage());
      }
      cmsSVResult.setSignedDocument(signedDocumentBytes);

      //Get algorithms and public key type. Note that the source of these values is the SVA signature which is regarded as the algorithm
      //That is effectively protecting the integrity of the signature, superseding the use of the original algorithms.
      SignedJWT signedJWT = svtValResult.getSignedJWT();
      JWSAlgorithm svtJwsAlgo = signedJWT.getHeader().getAlgorithm();

      String algoUri = JWSAlgorithmRegistry.getUri(svtJwsAlgo);
      cmsSVResult.setSignatureAlgorithm(algoUri);
      PubKeyParams pkParams = GeneralCMSUtils.getPkParams(getCert(svtValResult.getSignerCertificate()).getPublicKey());
      cmsSVResult.setPubKeyParams(pkParams);

      //Set signed SVT JWT
      cmsSVResult.setSvtJWT(signedJWT);

      /**
       * Set the signature certs as the result certs and set the validated certs as the validated path in cert validation results
       * The reason for this is that the SVT issuer must decide whether to just include a hash of the certs in the signature
       * or to include all explicit certs of the validated path. The certificates in the CertificateValidationResult represents the
       * validated path. If the validation was done by SVT, then the certificates obtained from SVT validation represents the validated path
       */
      // Get the signature certificates
      CMSSignedDataParser cmsSignedDataParser = CMSVerifyUtils.getCMSSignedDataParser(signature, pdfDocBytes);
      CMSTypedStream signedContent = cmsSignedDataParser.getSignedContent();
      signedContent.drain();
      GeneralCMSUtils.CMSSigCerts CMSSigCerts = GeneralCMSUtils.extractCertificates(cmsSignedDataParser);
      cmsSVResult.setSignerCertificate(CMSSigCerts.getSigCert());
      cmsSVResult.setSignatureCertificateChain(CMSSigCerts.getChain());
      // Store the svt validated certificates as path of certificate validation results
      CertificateValidationResult cvr = new DefaultCertificateValidationResult(SVAUtils.getOrderedCertList(svtValResult.getSignerCertificate(), svtValResult.getCertificateChain()));
      cmsSVResult.setCertificateValidationResult(cvr);

      // Finalize
      SignatureClaims signatureClaims = svtValResult.getSignatureClaims();
      if (svtValResult.isSvtValidationSuccess()) {
        cmsSVResult.setStatus(SignatureValidationResult.Status.SUCCESS);
      }
      else {
        cmsSVResult.setStatus(SignatureValidationResult.Status.ERROR_INVALID_SIGNATURE);
        cmsSVResult.setStatusMessage("Unable to verify SVT signature");
      }
      cmsSVResult.setSignatureClaims(signatureClaims);
      cmsSVResult.setValidationPolicyResultList(signatureClaims.getSig_val());
      // Since we verify with SVA. We ignore any present signature timestamps.
      // cmsSVResult.setSignatureTimeStampList(new ArrayList<>());

      //Add SVT document timestamp that was used to perform this SVT validation to verified times
      //This ensures that this time stamp gets added when SVT issuance is based on a previous SVT.
      JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
      List<TimeValidationClaims> timeValidationClaimsList = signatureClaims.getTime_val();
      timeValidationClaimsList.add(TimeValidationClaims.builder()
        .iss(jwtClaimsSet.getIssuer())
        .time(jwtClaimsSet.getIssueTime().getTime() / 1000)
        .type(SigValIdentifiers.TIME_VERIFICATION_TYPE_SVT)
        .id(jwtClaimsSet.getJWTID())
        .val(Arrays.asList(PolicyValidationClaims.builder()
          // TODO Get policy from certificate validator
          .pol(SigValIdentifiers.SIG_VALIDATION_POLICY_PKIX_VALIDATION)
          .res(ValidationConclusion.PASSED)
          .build()))
        .build());
      cmsSVResult.setTimeValidationResults(timeValidationClaimsList.stream()
        .map(timeValidationClaims -> new TimeValidationResult(
          timeValidationClaims, null, null))
        .collect(Collectors.toList())
      );

    }
    catch (Exception ex) {
      cmsSVResult.setStatus(SignatureValidationResult.Status.ERROR_INVALID_SIGNATURE);
      cmsSVResult.setStatusMessage("Unable to process SVA token or signature data");
      return cmsSVResult;
    }
    return cmsSVResult;
  }

  /**
   * Compare if the signature value match any of the listed SVT validation results
   * @param sigValueBytes signature value bytes
   * @param svtValidationResults validation result from SVT validation
   * @return The SVT validation results, or null on no match
   */
  private SignatureSVTValidationResult getMatchingSvtValidation(byte[] sigValueBytes,
    List<SignatureSVTValidationResult> svtValidationResults) {
    if (svtValidationResults == null) return null;
    for (SignatureSVTValidationResult svtValResult : svtValidationResults) {
      try {
        MessageDigest md = SVTAlgoRegistry.getMessageDigestInstance(svtValResult.getSignedJWT().getHeader().getAlgorithm());
        String sigValueHashStr = Base64.encodeBase64String(md.digest(sigValueBytes));
        if (sigValueHashStr.equals(svtValResult.getSignatureClaims().getSig_ref().getSig_hash()) && svtValResult.isSvtValidationSuccess()) {
          return svtValResult;
        }
      }
      catch (NoSuchAlgorithmException e) {
        continue;
      }
    }
    return null;
  }

  private ExtendedPdfSigValResult getErrorResult(PDSignature signature, String message) {
    ExtendedPdfSigValResult sigResult = new ExtendedPdfSigValResult();
    sigResult.setPdfSignature(signature);
    sigResult.setStatus(SignatureValidationResult.Status.ERROR_INVALID_SIGNATURE);
    sigResult.setStatusMessage("Failed to process signature: " + message);
    return sigResult;
  }

  private X509Certificate getCert(byte[] certBytes) throws CertificateException {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
  }

  @SuppressWarnings("unused")
  private List<X509Certificate> getCertList(List<byte[]> certificateChain) throws CertificateException {
    List<X509Certificate> certList = new ArrayList<>();
    for (byte[] certBytes : certificateChain) {
      certList.add(getCert(certBytes));
    }
    return certList;
  }

  /**
   * Compile a complete PDF signature verification result object from the list of individual signature results
   *
   * @param pdfDocBytes validate the complete PDF document and return concluding validation results for the complete document.
   * @return PDF signature validation result objects
   */
  @Override
  public SignedDocumentValidationResult<ExtendedPdfSigValResult> extendedResultValidation(byte[] pdfDocBytes) throws SignatureException{
    List<SignatureValidationResult> validationResults = validate(pdfDocBytes);
    return getConcludingSigVerifyResult(validationResults);
  }

  /**
   * Compile a complete PDF signature verification result object from the list of individual signature results
   *
   * @param sigVerifyResultList list of individual signature validation results. Each result must be of type {@link ExtendedPdfSigValResult}
   * @return PDF signature validation result objects
   */
  public static SignedDocumentValidationResult<ExtendedPdfSigValResult> getConcludingSigVerifyResult(
    List<SignatureValidationResult> sigVerifyResultList) {
    SignedDocumentValidationResult<ExtendedPdfSigValResult> sigVerifyResult = new SignedDocumentValidationResult<>();
    List<ExtendedPdfSigValResult> extendedPdfSigValResults = new ArrayList<>();
    try {
      extendedPdfSigValResults = sigVerifyResultList.stream()
        .map(signatureValidationResult -> (ExtendedPdfSigValResult) signatureValidationResult)
        .collect(Collectors.toList());
      sigVerifyResult.setSignatureValidationResults(extendedPdfSigValResults);
    }
    catch (Exception ex) {
      throw new IllegalArgumentException("Provided results are not instances of ExtendedPdfSigValResult");
    }
    //sigVerifyResult.setDocTimeStampList(docTimeStampList);
    // Test if there are no signatures
    if (sigVerifyResultList.isEmpty()) {
      sigVerifyResult.setSignatureCount(0);
      sigVerifyResult.setStatusMessage("No signatures");
      sigVerifyResult.setValidSignatureCount(0);
      sigVerifyResult.setCompleteSuccess(false);
      sigVerifyResult.setSigned(false);
      return sigVerifyResult;
    }

    //Get valid signatures
    sigVerifyResult.setSigned(true);
    sigVerifyResult.setSignatureCount(sigVerifyResultList.size());
    List<ExtendedPdfSigValResult> validSignatureResultList = extendedPdfSigValResults.stream()
      .filter(cmsSigVerifyResult -> cmsSigVerifyResult.isSuccess())
      .collect(Collectors.toList());

    sigVerifyResult.setValidSignatureCount(validSignatureResultList.size());
    if (validSignatureResultList.isEmpty()) {
      //No valid signatures
      sigVerifyResult.setCompleteSuccess(false);
      sigVerifyResult.setStatusMessage("No valid signatures");
      return sigVerifyResult;
    }

    //Reaching this point means that there are valid signatures.
    if (sigVerifyResult.getSignatureCount() == validSignatureResultList.size()) {
      sigVerifyResult.setStatusMessage("OK");
      sigVerifyResult.setCompleteSuccess(true);
    }
    else {
      sigVerifyResult.setStatusMessage("Some signatures are valid and some are invalid");
      sigVerifyResult.setCompleteSuccess(false);
    }

    //Check if any valid signature signs the whole document
    boolean validSigSignsWholeDoc = validSignatureResultList.stream()
      .filter(signatureValidationResult -> signatureValidationResult.isCoversDocument())
      .findFirst().isPresent();

    sigVerifyResult.setValidSignatureSignsWholeDocument(validSigSignsWholeDoc);

    return sigVerifyResult;
  }

}
