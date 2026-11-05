package net.yudichev.googlephotosupload.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Locale;
import java.util.stream.Stream;

/// Keeps host name lookups off the local network: this machine's own host name and any multicast DNS (`.local`) name resolve to the loopback address,
/// everything else goes to the built-in resolver.
///
/// Libraries look up the local host name for cosmetic reasons (Log4j does on every start, to offer it as a `${hostName}` property). On macOS that name
/// is usually a `.local` one, resolving it sends an mDNS query, and macOS then asks the user to allow the app to "find devices on your local network" —
/// alarming for an app that only talks to Google. Registered in `META-INF/services`.
public final class NoLocalNetworkResolverProvider extends InetAddressResolverProvider {
    @Override
    public InetAddressResolver get(Configuration configuration) {
        var builtinResolver = configuration.builtinResolver();
        var localHostName = configuration.lookupLocalHostName().toLowerCase(Locale.ROOT);
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
                var normalisedHost = host.toLowerCase(Locale.ROOT);
                if (normalisedHost.equals(localHostName) || normalisedHost.endsWith(".local") || normalisedHost.endsWith(".local.")) {
                    return Stream.of(InetAddress.getLoopbackAddress());
                }
                return builtinResolver.lookupByName(host, lookupPolicy);
            }

            @Override
            public String lookupByAddress(byte[] address) throws UnknownHostException {
                return builtinResolver.lookupByAddress(address);
            }
        };
    }

    @Override
    public String name() {
        return "no-local-network";
    }
}
