package org.amv.access.sdk.sample.logic;

import android.content.Context;

import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.error.AccessSdkException;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

import static com.google.common.base.Preconditions.checkNotNull;

public class CertificatesController implements ICertificatesController {

    private final CertificateManager certificateManager;

    private ICertificatesView view;

    public CertificatesController(CertificateManager certificateManager) {
        this.certificateManager = checkNotNull(certificateManager);
    }

    @Override
    public void initialize(ICertificatesView view, Context context) {
        this.view = checkNotNull(view);

        view.onInitializeFinished();
    }

    @Override
    public Observable<DeviceCertificate> getDeviceCertificate() {
        return certificateManager.getDeviceCertificate()
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Observable<AccessCertificatePair> getAccessCertificates() {
        return certificateManager.getAccessCertificates()
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void downloadCertificates() {
        certificateManager.refreshAccessCertificates()
                // add a delay to simulate real network
                .delay(500, TimeUnit.MILLISECONDS)
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(certs -> {
                    view.onCertificatesDownloaded(certs);
                }, e -> {
                    view.onCertificatesDownloadFailed(AccessSdkException.wrap(e));
                });
    }

    @Override
    public void revokeCertificate(AccessCertificatePair accessCertificatePair) {
        checkNotNull(accessCertificatePair);

        certificateManager.revokeAccessCertificate(accessCertificatePair)
                .flatMap(foo -> certificateManager.getAccessCertificates())
                // add a delay to simulate real network
                .delay(500, TimeUnit.MILLISECONDS)
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(accessCertificates -> {
                    view.onCertificateRevoked(accessCertificates);
                }, e -> {
                    view.onCertificateRevokeFailed(AccessSdkException.wrap(e));
                });
    }
}
