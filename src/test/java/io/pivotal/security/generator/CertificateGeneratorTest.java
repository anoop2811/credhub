package io.pivotal.security.generator;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static com.greghaskins.spectrum.Spectrum.let;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.credential.CertificateCredentialValue;
import io.pivotal.security.data.CertificateAuthorityService;
import io.pivotal.security.domain.CertificateParameters;
import io.pivotal.security.request.CertificateGenerationParameters;
import io.pivotal.security.util.CertificateFormatter;
import io.pivotal.security.util.CurrentTimeProvider;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Supplier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.runner.RunWith;

@RunWith(Spectrum.class)
public class CertificateGeneratorTest {

  private CertificateGenerator subject;

  private LibcryptoRsaKeyPairGenerator keyGenerator;
  private SignedCertificateGenerator signedCertificateGenerator;
  private CertificateAuthorityService certificateAuthorityService;

  private FakeKeyPairGenerator fakeKeyPairGenerator;

  private X500Name rootCaDn;
  private X500Name signeeDn;
  private KeyPair rootCaKeyPair;
  private CertificateCredentialValue rootCa;
  private X509Certificate rootCaX509Certificate;

  private X500Name intermediateCaDn;
  private KeyPair intermediateCaKeyPair;
  private CertificateCredentialValue intermediateCa;
  private X509Certificate intermediateX509Certificate;

  private CertificateParameters inputParameters;
  private CertificateGenerationParameters generationParameters;
  private X509Certificate childX509Certificate;

  {
    beforeEach(() -> {
      keyGenerator = mock(LibcryptoRsaKeyPairGenerator.class);
      signedCertificateGenerator = mock(SignedCertificateGenerator.class);
      certificateAuthorityService = mock(CertificateAuthorityService.class);

      subject = new CertificateGenerator(keyGenerator, signedCertificateGenerator,
          certificateAuthorityService);

      fakeKeyPairGenerator = new FakeKeyPairGenerator();

      rootCaDn = new X500Name("O=foo,ST=bar,C=root");
      signeeDn = new X500Name("O=foo,ST=bar,C=mars");
      rootCaKeyPair = fakeKeyPairGenerator.generate();
      X509CertificateHolder caX509CertHolder = makeCert(rootCaKeyPair, rootCaKeyPair.getPrivate(),
          rootCaDn, rootCaDn, true);
      rootCaX509Certificate = new JcaX509CertificateConverter()
          .setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(caX509CertHolder);
      rootCa = new CertificateCredentialValue(
          null,
          CertificateFormatter.pemOf(rootCaX509Certificate),
          CertificateFormatter.pemOf(rootCaKeyPair.getPrivate()),
          null);

      generationParameters = new CertificateGenerationParameters();
      generationParameters.setOrganization("foo");
      generationParameters.setState("bar");
      generationParameters.setCaName("my-ca-name");
      generationParameters.setCountry("mars");
      generationParameters.setDuration(365);

      inputParameters = new CertificateParameters(generationParameters);
    });

    describe("when CA exists", () -> {
      final Supplier<KeyPair> childCertificateKeyPair = let(() -> fakeKeyPairGenerator.generate());

      describe("and it is a root CA", () -> {
        beforeEach(() -> {
          when(certificateAuthorityService.findMostRecent("my-ca-name")).thenReturn(rootCa);

          when(keyGenerator.generateKeyPair(anyInt())).thenReturn(childCertificateKeyPair.get());

          X509CertificateHolder childCertificateHolder = generateChildCertificateSignedByCa(
              childCertificateKeyPair.get(),
              rootCaKeyPair.getPrivate(),
              rootCaDn
          );

          childX509Certificate = new JcaX509CertificateConverter()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(childCertificateHolder);

          when(
              signedCertificateGenerator
                  .getSignedByIssuer(childCertificateKeyPair.get(), inputParameters, rootCa)
          ).thenReturn(childX509Certificate);
        });

        it("generates a valid childCertificate", () -> {
          CertificateCredentialValue certificateSignedByRoot = subject.generateCredential(inputParameters);

          assertThat(certificateSignedByRoot.getCa(),
              equalTo(rootCa.getCertificate()));

          assertThat(certificateSignedByRoot.getPrivateKey(),
              equalTo(CertificateFormatter.pemOf(childCertificateKeyPair.get().getPrivate())));

          assertThat(certificateSignedByRoot.getCertificate(),
              equalTo(CertificateFormatter.pemOf(childX509Certificate)));

          assertThat(certificateSignedByRoot.getCaName(), equalTo("my-ca-name"));

          verify(keyGenerator, times(1)).generateKeyPair(2048);
        });

        it("generates a valid childCertificate when a key length is given", () -> {
          generationParameters.setKeyLength(4096);
          CertificateParameters params = new CertificateParameters(generationParameters);

          when(
              signedCertificateGenerator
                  .getSignedByIssuer(childCertificateKeyPair.get(), params, rootCa)
          ).thenReturn(childX509Certificate);

          CertificateCredentialValue certificate = subject.generateCredential(
              params);

          assertThat(certificate, notNullValue());
          verify(keyGenerator, times(1)).generateKeyPair(4096);
        });
      });

      describe("and it is an intermediate CA", () -> {
        beforeEach(() -> {
          intermediateCaDn = new X500Name("O=foo,ST=bar,C=intermediate");
          intermediateCaKeyPair = fakeKeyPairGenerator.generate();
          X509CertificateHolder intermediateCaCertificateHolder = makeCert(intermediateCaKeyPair,
              rootCaKeyPair.getPrivate(), rootCaDn, intermediateCaDn, true);
          intermediateX509Certificate = new JcaX509CertificateConverter()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(intermediateCaCertificateHolder);
          intermediateCa = new CertificateCredentialValue(
              null,
              CertificateFormatter.pemOf(intermediateX509Certificate),
              CertificateFormatter.pemOf(intermediateCaKeyPair.getPrivate()),
              null);
          when(certificateAuthorityService.findMostRecent("my-ca-name")).thenReturn(intermediateCa);

          when(keyGenerator.generateKeyPair(anyInt())).thenReturn(childCertificateKeyPair.get());

          X509CertificateHolder childCertificateHolder = generateChildCertificateSignedByCa(
              childCertificateKeyPair.get(),
              intermediateCaKeyPair.getPrivate(),
              intermediateCaDn
          );

          childX509Certificate = new JcaX509CertificateConverter()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(childCertificateHolder);

          when(
              signedCertificateGenerator
                  .getSignedByIssuer(childCertificateKeyPair.get(), inputParameters, intermediateCa)
          ).thenReturn(childX509Certificate);
        });

        it("generates a valid childCertificate", () -> {
          CertificateCredentialValue certificateSignedByIntermediate = subject.generateCredential(inputParameters);

          assertThat(certificateSignedByIntermediate.getCa(),
              equalTo(intermediateCa.getCertificate()));

          assertThat(certificateSignedByIntermediate.getPrivateKey(),
              equalTo(CertificateFormatter.pemOf(childCertificateKeyPair.get().getPrivate())));

          assertThat(certificateSignedByIntermediate.getCertificate(),
              equalTo(CertificateFormatter.pemOf(childX509Certificate)));

          verify(keyGenerator, times(1)).generateKeyPair(2048);
        });
      });
    });

    describe("when the selfSign flag is set", () -> {
      final Supplier<X509Certificate> certificate = let(() ->
          new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(generateX509SelfSignedCert())
      );

      beforeEach(() -> {
        generationParameters.setCaName(null);
        generationParameters.setSelfSigned(true);
        inputParameters = new CertificateParameters(generationParameters);
        when(keyGenerator.generateKeyPair(anyInt())).thenReturn(rootCaKeyPair);
        when(signedCertificateGenerator.getSelfSigned(rootCaKeyPair, inputParameters))
            .thenReturn(certificate.get());
      });

      it("generates a valid self-signed certificate", () -> {
        CertificateCredentialValue certificateCredential = subject.generateCredential(inputParameters);
        assertThat(certificateCredential.getPrivateKey(),
            equalTo(CertificateFormatter.pemOf(rootCaKeyPair.getPrivate())));
        assertThat(certificateCredential.getCertificate(),
            equalTo(CertificateFormatter.pemOf(certificate.get())));
        assertThat(certificateCredential.getCa(), nullValue());
        verify(signedCertificateGenerator, times(1)).getSelfSigned(rootCaKeyPair, inputParameters);
      });
    });
  }

  private X509CertificateHolder generateX509SelfSignedCert() throws Exception {
    return makeCert(rootCaKeyPair, rootCaKeyPair.getPrivate(), rootCaDn, rootCaDn, false);
  }

  private X509CertificateHolder generateChildCertificateSignedByCa(KeyPair certKeyPair,
      PrivateKey caPrivateKey,
      X500Name caDn) throws Exception {
    return makeCert(certKeyPair, caPrivateKey, caDn, signeeDn, false);
  }

  private X509CertificateHolder makeCert(KeyPair certKeyPair,
      PrivateKey caPrivateKey,
      X500Name caDn,
      X500Name subjectDn,
      boolean isCa) throws OperatorCreationException, NoSuchAlgorithmException, CertIOException {
    SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(certKeyPair.getPublic()
        .getEncoded());
    ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .build(caPrivateKey);

    CurrentTimeProvider currentTimeProvider = new CurrentTimeProvider();

    Instant now = currentTimeProvider.getNow().toInstant();

    X509v3CertificateBuilder x509v3CertificateBuilder = new X509v3CertificateBuilder(
        caDn,
        BigInteger.TEN,
        Date.from(now),
        Date.from(now.plus(Duration.ofDays(365))),
        subjectDn,
        publicKeyInfo
    );
    x509v3CertificateBuilder
        .addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));
    return x509v3CertificateBuilder.build(contentSigner);
  }
}
