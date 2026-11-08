package bj.rencontre;

import android.content.Context;
import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

/** Contrôle des numéros WhatsApp selon les règles de chaque pays (longueur, préfixes mobiles, indicatif). */
final class Telephone {
    private static PhoneNumberUtil util;

    private static synchronized PhoneNumberUtil u(Context c) {
        if (util == null) util = PhoneNumberUtil.createInstance(c.getApplicationContext());
        return util;
    }

    /** Indicatif du pays, ex : « +229 ». */
    static String indicatif(Context c, String region) {
        int i = u(c).getCountryCodeForRegion(region);
        return i > 0 ? "+" + i : "";
    }

    /** Exemple de numéro mobile du pays, ex : « +229 01 90 01 12 34 ». */
    static String exemple(Context c, String region) {
        PhoneNumberUtil p = u(c);
        Phonenumber.PhoneNumber ex = p.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE);
        if (ex == null) return indicatif(c, region) + " …";
        return p.format(ex, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    }

    /**
     * Renvoie le numéro au format international sans « + » (ex : 22901901234 pour wa.me),
     * ou null si le numéro n'est pas un mobile valide du pays choisi.
     * Accepte la saisie avec ou sans l'indicatif.
     */
    static String valider(Context c, String saisie, String region) {
        PhoneNumberUtil p = u(c);
        String chiffres = saisie == null ? "" : saisie.replaceAll("[^0-9]", "");
        if (chiffres.startsWith("00")) chiffres = chiffres.substring(2);
        if (chiffres.isEmpty()) return null;
        String ind = String.valueOf(p.getCountryCodeForRegion(region));
        String[] essais = new String[]{
                chiffres.startsWith(ind) ? "+" + chiffres : null, // saisi avec l'indicatif
                chiffres                                            // saisi au format national
        };
        for (int i = 0; i < essais.length; i++) {
            if (essais[i] == null) continue;
            try {
                Phonenumber.PhoneNumber n = p.parse(essais[i], i == 0 ? null : region);
                if (!p.isValidNumberForRegion(n, region)) continue;
                PhoneNumberUtil.PhoneNumberType t = p.getNumberType(n);
                if (t != PhoneNumberUtil.PhoneNumberType.MOBILE && t != PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE) continue;
                return p.format(n, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1);
            } catch (NumberParseException ignore) { }
        }
        return null;
    }
}
