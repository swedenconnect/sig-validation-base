package se.swedenconnect.sigval.commons.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.util.CollectionStore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import se.swedenconnect.sigval.commons.algorithms.NamedCurve;
import se.swedenconnect.sigval.commons.algorithms.NamedCurveRegistry;
import se.swedenconnect.sigval.commons.algorithms.PublicKeyType;
import se.swedenconnect.sigval.commons.data.PubKeyParams;

/**
 * Utility methods for processing CMS data
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class GeneralCMSUtils {

  /**
   * Extracts signing certificate and supporting certificate chain
   *
   * @param cmsSignedDataParser
   *          {@link CMSSignedDataParser} object holding certificate data
   * @return signing certificate and supporting certificate chain
   * @throws Exception
   *           is certificate extraction fails
   */
  public static CMSSigCerts extractCertificates(CMSSignedDataParser cmsSignedDataParser) throws Exception {
    CollectionStore<?> certStore = (CollectionStore<?>) cmsSignedDataParser.getCertificates();
    Iterator<?> ci = certStore.iterator();
    List<X509Certificate> certList = new ArrayList<>();
    while (ci.hasNext()) {
      certList.add(getCert((X509CertificateHolder) ci.next()));
    }
    SignerInformation signerInformation = cmsSignedDataParser.getSignerInfos().iterator().next();
    @SuppressWarnings("unchecked")
    Collection<?> certCollection = certStore.getMatches(signerInformation.getSID());
    X509Certificate sigCert = getCert((X509CertificateHolder) certCollection.iterator().next());
    return new CMSSigCerts(sigCert, certList);
  }

  /**
   * Obtains a {@link CMSSignedDataParser}
   *
   * @param cmsContentInfo
   *          The byes of the contents parameter in the signature dictionary containing the bytes of a CMS ContentInfo
   * @param signedDocBytes
   *          The bytes of the PDF document signed by this signature. These are the bytes identified by the byteRange
   *          parameter in the signature dictionary.
   * @return CMSSignedDataParser
   * @throws CMSException
   *           on error
   */
  public static CMSSignedDataParser getCMSSignedDataParser(byte[] cmsContentInfo, byte[] signedDocBytes) throws CMSException {
    ByteArrayInputStream bis = new ByteArrayInputStream(signedDocBytes);
    return new CMSSignedDataParser(new BcDigestCalculatorProvider(), new CMSTypedStream(bis), cmsContentInfo);
  }

  /**
   * Retrieves Public key parameters from a public key
   *
   * @param pubKey
   *          The public key
   * @return public key parameters
   * @throws IOException
   *           error obtaining public key parameters
   */
  public static PubKeyParams getPkParams(PublicKey pubKey) throws IOException {

    PubKeyParams pubKeyParams = new PubKeyParams();

    ASN1InputStream din = null;
    try {
      din = new ASN1InputStream(new ByteArrayInputStream(pubKey.getEncoded()));
      ASN1Sequence pkSeq = ASN1Sequence.getInstance(din.readObject());
      pkSeq.getObjectAt(1);

      AlgorithmIdentifier algoId = AlgorithmIdentifier.getInstance(pkSeq.getObjectAt(0));
      PublicKeyType pkType = PublicKeyType.getTypeFromOid(algoId.getAlgorithm().getId());
      pubKeyParams.setPkType(pkType);
      if (pkType.equals(PublicKeyType.EC)) {
        ASN1ObjectIdentifier curveOid = ASN1ObjectIdentifier.getInstance(algoId.getParameters());
        NamedCurve curve = NamedCurveRegistry.get(curveOid);
        pubKeyParams.setNamedEcCurve(curve);
        int totalKeyBits = curve.getKeyLen();
        pubKeyParams.setKeyLength(totalKeyBits);
        return pubKeyParams;
      }

      if (pkType.equals(PublicKeyType.RSA)) {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) pubKey;
        pubKeyParams.setKeyLength(rsaPublicKey.getModulus().bitLength());
        return pubKeyParams;
      }
    }
    catch (Exception e) {
      log.debug("Illegal public key parameters: " + e.getMessage());
    }
    finally {
      if (din != null) {
        try {
          din.close();
        }
        catch (IOException e) {
        }
      }
    }
    return null;
  }

  /**
   * This method extracts the ESSCertID sequence from a SigningCertificate signed CMS attribute. If the signed attribute
   * is of type SigningCertificateV2 (RFC 5035) the returned sequence is ESSCertIDv2. If the signed attribute is of type
   * SigningCertificate (RFC2634 using SHA1 as fixed hash algo) then the returned sequence is of type ESSCertID.
   *
   * @param essSigningCertAttr
   *          The signed CMS attribute carried in SignerInfo
   * @return An ASN.1 Sequence holding the sequence of objects in ESSCertID or ESSCertIDv2
   * @throws Exception
   *           Any exception caused by input not mathing the assumed processing rules
   */
  public static ASN1Sequence getESSCertIDSequence(Attribute essSigningCertAttr) throws Exception {
    /**
     * Attribute ::= SEQUENCE { attrType OBJECT IDENTIFIER, attrValues SET OF AttributeValue }
     */
    ASN1Encodable[] attributeValues = essSigningCertAttr.getAttributeValues();
    ASN1Sequence signingCertificateV2Seq = (ASN1Sequence) attributeValues[0]; // Holds sequence of certs and policy
    /**
     * -- RFC 5035 SigningCertificateV2 ::= SEQUENCE { certs SEQUENCE OF ESSCertIDv2, policies SEQUENCE OF
     * PolicyInformation OPTIONAL }
     *
     * -- RFC 2634 SigningCertificate ::= SEQUENCE { certs SEQUENCE OF ESSCertID, policies SEQUENCE OF PolicyInformation
     * OPTIONAL }
     */
    ASN1Sequence sequenceOfESSCertID = (ASN1Sequence) signingCertificateV2Seq.getObjectAt(0); // holds sequence of
                                                                                              // ESSCertID or
                                                                                              // ESSCertIDv2
    /**
     * ESSCertIDv2 ::= SEQUENCE { hashAlgorithm AlgorithmIdentifier DEFAULT {algorithm id-sha256}, certHash Hash,
     * issuerSerial IssuerSerial OPTIONAL }
     *
     * ESSCertID ::= SEQUENCE { certHash Hash, issuerSerial IssuerSerial OPTIONAL }
     */
    ASN1Sequence eSSCertIDSeq = (ASN1Sequence) sequenceOfESSCertID.getObjectAt(0); // Holds seq of objects in ESSCertID
                                                                                   // or ESSCertIDv2
    return eSSCertIDSeq;
  }

  /**
   * converts an X509CertificateHolder object to an X509Certificate object.
   *
   * @param certHolder
   *          the cert holder object
   * @return X509Certificate object
   * @throws IOException
   *           error parsing input data
   * @throws CertificateException
   *           certificate parsing error
   */
  public static X509Certificate getCert(X509CertificateHolder certHolder) throws IOException, CertificateException {
    X509Certificate cert;
    ByteArrayInputStream certIs = new ByteArrayInputStream(certHolder.getEncoded());

    try {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      cert = (X509Certificate) cf.generateCertificate(certIs);

    }
    finally {
      certIs.close();
    }

    return cert;
  }

  /**
   * Data class for providing certificates supporting a CMS signature
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CMSSigCerts {
    /** The signing certificate */
    private X509Certificate sigCert;
    /** Supporting chain */
    private List<X509Certificate> chain;
  }

}
