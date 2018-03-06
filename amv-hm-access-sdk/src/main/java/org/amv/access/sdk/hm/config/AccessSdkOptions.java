package org.amv.access.sdk.hm.config;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.certificate.Remote;
import org.amv.access.sdk.spi.identity.Identity;

public interface AccessSdkOptions {

    AccessApiContext getAccessApiContext();

    Optional<Identity> getIdentity();

    Optional<Remote> getRemote();
}
