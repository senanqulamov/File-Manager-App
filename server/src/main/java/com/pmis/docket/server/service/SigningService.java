package com.pmis.docket.server.service;

import com.pmis.docket.server.config.DocketProperties;
import com.pmis.docket.server.model.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Digital signatures (PAdES-compatible "adbe.pkcs7.detached") for PDFs.
 * Every user gets a personal signing certificate, created on first use and kept on the server
 * in storage-root/keys. They are issued by PMIS Docket itself; a company certificate authority
 * can replace them later without changing the documents' format.
 */
@Service
public class SigningService {
    private final DocketProperties props;
    private final StorageService storage;

    public SigningService(DocketProperties props, StorageService storage) {
        this.props = props;
        this.storage = storage;
    }

    public byte[] signPdf(byte[] pdf, User user, String reason) throws IOException {
        Identity id = identity(user);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            sig.setName(user.displayName);
            sig.setLocation(props.getCompanyName());
            sig.setReason(reason);
            sig.setSignDate(Calendar.getInstance());
            SignatureInterface signer = content -> cms(content, id);
            doc.addSignature(sig, signer);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.saveIncremental(out);
            return out.toByteArray();
        }
    }

    private byte[] cms(InputStream content, Identity id) throws IOException {
        try {
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA").build(id.key());
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(cs, id.cert()));
            gen.addCertificates(new JcaCertStore(List.of(id.cert())));
            CMSSignedData signed = gen.generate(new CMSProcessableByteArray(content.readAllBytes()), false);
            return signed.getEncoded();
        } catch (Exception e) {
            throw new IOException("Could not create the signature", e);
        }
    }

    public String certificateSubject(User user) {
        try {
            return identity(user).cert().getSubjectX500Principal().getName();
        } catch (IOException e) {
            return user.displayName;
        }
    }

    private synchronized Identity identity(User user) throws IOException {
        char[] pwd = props.getKeyPassword().toCharArray();
        Path dir = storage.root().resolve("keys");
        Files.createDirectories(dir);
        Path file = dir.resolve("user-" + user.id + ".p12");
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    ks.load(in, pwd);
                }
                return new Identity((PrivateKey) ks.getKey("sign", pwd), (X509Certificate) ks.getCertificate("sign"));
            }
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(3072);
            KeyPair kp = kpg.generateKeyPair();
            X500NameBuilder nb = new X500NameBuilder(BCStyle.INSTANCE);
            nb.addRDN(BCStyle.CN, user.displayName);
            nb.addRDN(BCStyle.O, props.getCompanyName());
            if (user.department != null) nb.addRDN(BCStyle.OU, user.department);
            nb.addRDN(BCStyle.UID, user.login);
            var subject = nb.build();
            Date from = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
            Date to = Date.from(Instant.now().plus(3 * 365, ChronoUnit.DAYS));
            X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.currentTimeMillis()), from, to, subject, kp.getPublic());
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(b.build(cs));
            ks.load(null, null);
            ks.setKeyEntry("sign", kp.getPrivate(), pwd, new Certificate[]{cert});
            try (OutputStream out = Files.newOutputStream(file)) {
                ks.store(out, pwd);
            }
            return new Identity(kp.getPrivate(), cert);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not prepare the signing certificate", e);
        }
    }

    private record Identity(PrivateKey key, X509Certificate cert) { }
}
