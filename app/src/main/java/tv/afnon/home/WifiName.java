package tv.afnon.home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import java.net.InetAddress;

/**
 * The Wi-Fi network's name for Home's pill where Android won't give it. From Android 9 an app reads the name only
 * with the location permission and Location turned on for the whole TV, and many TVs keep Location off. The shell
 * can read it without either (`cmd wifi status`), so setup hands it over once (SetupProvider, setting "wifi_name")
 * and it is kept with the network's gateway and DNS servers, which Android shows without Location. On that network
 * the pill shows the name; on any other it says Wi-Fi.
 */
final class WifiName {
    private WifiName() {}

    private static final String PREF = "wifi_names";
    /** Networks remembered, newest first: enough for a TV that moves between a few. */
    private static final int KEEP = 4;

    /** The name saved for the network the TV is on now, or null. */
    static String known(Context c) {
        String here = fingerprint(c);
        if (here == null) return null;
        for (String line : Prefs.of(c).getString(PREF, "").split("\n")) {
            int eq = line.indexOf('\t');
            if (eq > 0 && line.substring(0, eq).equals(here)) return line.substring(eq + 1);
        }
        return null;
    }

    /** Remembers name for the network the TV is on now; false when it isn't on Wi-Fi. */
    static boolean remember(Context c, String name) {
        String here = fingerprint(c);
        if (here == null || name.isEmpty() || name.contains("\n") || name.contains("\t")) return false;
        StringBuilder out = new StringBuilder(here).append('\t').append(name);
        int kept = 1;
        for (String line : Prefs.of(c).getString(PREF, "").split("\n")) {
            if (line.isEmpty() || line.startsWith(here + "\t") || kept >= KEEP) continue;
            out.append('\n').append(line);
            kept++;
        }
        Prefs.of(c).edit().putString(PREF, out.toString()).apply();
        return true;
    }

    /** "gateway|dns,dns" for the Wi-Fi network in use, or null when it isn't Wi-Fi or can't be read. */
    private static String fingerprint(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (caps == null || lp == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null;
            String gateway = "";
            for (RouteInfo r : lp.getRoutes()) {
                if (r.isDefaultRoute() && r.getGateway() != null) gateway = r.getGateway().getHostAddress();
            }
            StringBuilder dns = new StringBuilder();
            for (InetAddress a : lp.getDnsServers()) {
                if (dns.length() > 0) dns.append(',');
                dns.append(a.getHostAddress());
            }
            if (gateway.isEmpty() && dns.length() == 0) return null;
            return gateway + "|" + dns;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
