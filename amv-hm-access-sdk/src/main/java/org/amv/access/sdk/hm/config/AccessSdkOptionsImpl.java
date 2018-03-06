package org.amv.access.sdk.hm.config;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.certificate.Remote;
import org.amv.access.sdk.spi.identity.Identity;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AccessSdkOptionsImpl implements AccessSdkOptions {

    @NonNull
    private AccessApiContext accessApiContext;

    private Identity identity;
    private Remote remote;

    @Override
    public Optional<Identity> getIdentity() {
        return Optional.fromNullable(identity);
    }

    @Override
    public Optional<Remote> getRemote() {
        return Optional.fromNullable(remote);
    }
}
