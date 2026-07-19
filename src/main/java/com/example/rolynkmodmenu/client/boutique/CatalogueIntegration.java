package com.example.rolynkmodmenu.client.boutique;

/**
 * Pont OPTIONNEL vers le catalogue premium de RolynkRP (mod rolynkcatalogue).
 *
 * Tout passe par réflexion — aucun import direct : si le mod catalogue est
 * absent (ou son API incompatible), on retourne false et l'appelant affiche
 * son écran « à venir ». Même pattern défensif que BoosterIntegration
 * (rolynk_cards) côté serveur.
 */
public final class CatalogueIntegration {

    private CatalogueIntegration() {}

    /**
     * Ouvre le catalogue RolynkRP directement sur une section (ex. "Pets").
     * @return true si l'écran a été ouvert, false si le mod est absent.
     */
    public static boolean openSection(String section) {
        try {
            Class<?> screen = Class.forName("com.axis.rolynkcatalogue.gui.CatalogueScreen");
            screen.getMethod("openSection", String.class).invoke(null, section);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
