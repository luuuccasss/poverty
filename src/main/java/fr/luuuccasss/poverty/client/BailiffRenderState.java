package fr.luuuccasss.poverty.client;

/** Interface ajoutée à l'état de rendu des zombies villageois par mixin : est-ce l'huissier ? */
public interface BailiffRenderState {
    boolean poverty$isBailiff();

    void poverty$setBailiff(boolean bailiff);
}
