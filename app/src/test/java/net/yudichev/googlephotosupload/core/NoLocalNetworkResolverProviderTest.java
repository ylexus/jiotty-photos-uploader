package net.yudichev.googlephotosupload.core;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/// The provider is registered in `META-INF/services`, so it is the resolver of this test JVM.
final class NoLocalNetworkResolverProviderTest {
    @Test
    void resolvesLocalHostNameToLoopback() throws Exception {
        assertThat(InetAddress.getLocalHost().getAddress(), equalTo(InetAddress.getLoopbackAddress().getAddress()));
    }

    @Test
    void resolvesMulticastDnsNamesToLoopback() throws Exception {
        assertThat(InetAddress.getByName("Some-Printer.LOCAL").getAddress(), equalTo(InetAddress.getLoopbackAddress().getAddress()));
    }

    @Test
    void leavesAddressLiteralsAlone() throws Exception {
        assertThat(InetAddress.getByName("192.0.2.1").getHostAddress(), equalTo("192.0.2.1"));
    }
}
