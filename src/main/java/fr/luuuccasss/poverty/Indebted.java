package fr.luuuccasss.poverty;

/** Interface ajoutée à ServerPlayerEntity par mixin : le compte du joueur à la banque du village. */
public interface Indebted {
    /** Reste à rembourser, en émeraudes. */
    long poverty$getDebt();

    void poverty$setDebt(long debt);

    /** Intérêts échus et pas encore payés. */
    long poverty$getArrears();

    void poverty$setArrears(long arrears);

    /** Nombre de jours d'affilée sans paiement. */
    int poverty$getUnpaidDays();

    void poverty$setUnpaidDays(int days);

    /** Dernier jour du monde où les intérêts ont été comptés, -1 si le prêt n'a pas encore été accordé. */
    long poverty$getLastDay();

    void poverty$setLastDay(long day);
}
