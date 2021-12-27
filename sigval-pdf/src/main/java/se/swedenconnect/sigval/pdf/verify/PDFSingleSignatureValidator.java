package se.swedenconnect.sigval.pdf.verify;

import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import se.idsec.signservice.security.certificate.CertificateValidator;
import se.swedenconnect.sigval.pdf.data.ExtendedPdfSigValResult;
import se.swedenconnect.sigval.pdf.pdfstruct.PDFSignatureContext;
import se.swedenconnect.sigval.pdf.timestamp.PDFDocTimeStamp;

import java.util.List;

/**
 * Interface for a verifier used to verify a single signature in a PDF document
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public interface PDFSingleSignatureValidator {

  /**
   * Verifies the signature on a PDF document
   *
   * @param signature PDF signature
   * @param pdfDocument PDF document
   * @param documentTimestamps document timestamps of the signed PDF document
   * @param signatureContext signature context data
   * @return Signature validation result
   * @throws Exception on errors during signature validation
   */
  ExtendedPdfSigValResult verifySignature (PDSignature signature, byte[] pdfDocument, List<PDFDocTimeStamp> documentTimestamps, PDFSignatureContext signatureContext) throws Exception;

  /**
   * Verifies document timestamps
   * @param documentTimestampSignatures list of PDF signatures holding document timestamps
   * @param pdfDocument the PDF document bytes of the PDF document containing the document timestamps
   * @return list of PDF document timestamp objects {@link PDFDocTimeStamp}
   */
  List<PDFDocTimeStamp> verifyDocumentTimestamps (List<PDSignature> documentTimestampSignatures, byte[] pdfDocument);

  /**
   * Returns the certificate validator used to validate certificates
   * @return certificate validator
   */
  CertificateValidator getCertificateValidator();

}
