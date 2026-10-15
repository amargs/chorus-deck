package org.amargs.chorusdeck.liturgia;

import org.amargs.chorusdeck.model.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiturgyPageTest {

    @Test
    void ordinarySundaySharesTheGospelSentenceAndComputesTheYear() {
        LiturgyDay day = LiturgyPage.parse(LocalDate.of(2026, 10, 18), """
                <h4>DOMINGO XXIX DO TEMPO COMUM</h4>
                <p>Verde – Ofício do domingo.</p>
                <h3>Missa</h3>
                <p>LEITURA I Is 45, 1.4-6</p>
                <p>&laquo;Tomei Ciro pela mão direita&raquo;</p>
                <p>O Evangelho de hoje vai afirmar a relação. Leitura do Livro de Isaías Assim fala o Senhor.</p>
                <p>SALMO RESPONSORIAL Salmo 95 (96), 1.3 (R. 7b)</p>
                <p>Refrão: Aclamai a glória e o poder do Senhor. Repete-se</p>
                <p>Ou: Outro refrão qualquer. Repete-se</p>
                <p>Cantai ao Senhor um cântico novo. Refrão</p>
                <p>LEITURA II 1Ts 1, 1-5b «Recordamos a vossa fé, caridade e esperança»</p>
                <p>Começamos hoje a leitura. Leitura da Primeira Epístola Paulo, Silvano e Timóteo.</p>
                <p>ALELUIA Flp 2, 15d.16a Refrão: Aleluia. Repete-se</p>
                <p>EVANGELHO Mt 22, 15-21 «Dai a César o que é de César e a Deus o que é de Deus»</p>
                <p>Perante o poder temporal. Evangelho de Nosso Senhor Jesus Cristo segundo São Mateus Naquele tempo.</p>
                """);

        assertEquals("Domingo XXIX", day.domingo());
        assertEquals("comum", day.seasonKey());
        assertNull(day.coverLayout());
        assertEquals("A", day.ano());
        assertEquals("Is 45, 1.4-6", day.primeira().referencia());
        assertEquals("Tomei Ciro pela mão direita", day.primeira().citacao());
        assertEquals("1Ts 1, 1-5b", day.segunda().referencia());
        assertEquals("Recordamos a vossa fé, caridade e esperança", day.segunda().citacao());
        assertEquals("Aclamai a glória e o poder do Senhor.", day.refrao());
        assertEquals("Mt 22, 15-21", day.evangelho().referencia());
        assertEquals("Dai a César o que é de César e a Deus o que é de Deus", day.evangelho().citacao());
        assertEquals(day.evangelho().citacao(), day.cover().frase());
        assertEquals("«" + day.evangelho().citacao() + "»", day.cover().quotedFrase());
        assertEquals(day.evangelho().referencia(), day.cover().versiculo());
    }

    @Test
    void gaudeteUsesThePrintedYearAndTheRoseCover() {
        LiturgyDay day = LiturgyPage.parse(LocalDate.of(2026, 12, 13), """
                <h4>DOMINGO III DO ADVENTO</h4>
                <p>Ano B</p>
                <p>LEITURA I Is 61, 1-2a.10-11 «Exulto de alegria no Senhor»</p>
                <p>Leitura do Livro de Isaías O espírito do Senhor está sobre mim.</p>
                <p>SALMO RESPONSORIAL Lc 1, 46-48.49-50 (R. Is 61, 10b)</p>
                <p>Refrão: Exulto de alegria no Senhor. Repete-se</p>
                <p>Ou: A minha alma exulta no Senhor. Repete-se</p>
                <p>LEITURA II 1 Tes 5, 16-24 «Todo o vosso ser – espírito, alma e corpo – se conserve para a vinda do Senhor»</p>
                <p>Leitura da Primeira Epístola Irmãos: Vivei sempre alegres.</p>
                <p>EVANGELHO Jo 1, 6-8.19-28 «No meio de vós está Alguém que não conheceis»</p>
                <p>Evangelho de Nosso Senhor Jesus Cristo segundo São João Apareceu um homem.</p>
                """);

        assertEquals("Domingo III", day.domingo());
        assertEquals("advento", day.seasonKey());
        assertEquals("Capa - Domingo III Advento", day.coverLayout());
        assertEquals("B", day.ano());
        assertEquals("Is 61, 1-2a.10-11", day.primeira().referencia());
        assertEquals("1 Tes 5, 16-24", day.segunda().referencia());
        assertEquals("Exulto de alegria no Senhor.", day.refrao());
        assertTrue(Season.resolve(day.seasonKey(), day.coverLayout()).omitsGloria());
        assertEquals("Tempo de Advento · Ano B", Season.resolve(day.seasonKey(), day.coverLayout()).tempo(day.ano()));
    }

    @Test
    void palmSundayUsesThePassionNotTheProcessionGospel() {
        LiturgyDay day = LiturgyPage.parse(LocalDate.of(2026, 3, 29), """
                <h4>DOMINGO DE RAMOS NA PAIXÃO DO SENHOR</h4>
                <p>Evangelho Mt 21, 1-11 «Bendito o que vem em nome do Senhor»</p>
                <p>LITURGIA DA PALAVRA</p>
                <p>LEITURA I Is. 50, 4-7 «Não desviei o meu rosto»</p>
                <p>Leitura do Livro de Isaías O Senhor deu-me a graça.</p>
                <p>SALMO RESPONSORIAL Sal. 21 (22), 8-9 (R. 2a) Refrão: Meu Deus, meu Deus, porque me abandonastes? Repete-se</p>
                <p>LEITURA II Filip 2, 6-11 «Humilhou-Se a Si próprio»</p>
                <p>Leitura da Epístola Cristo Jesus, que era de condição divina.</p>
                <p>EVANGELHO – Forma longa Mt 26, 14 – 27, 66 Paixão de Nosso Senhor Jesus Cristo</p>
                <p>Todos os evangelistas apresentam a história da Paixão do Senhor.</p>
                <p>EVANGELHO – Forma breve Mt 27, 11-54 «Jesus foi levado à presença de Pilatos»</p>
                """);

        assertEquals("Domingo de Ramos na Paixão do Senhor", day.domingo());
        assertEquals("ramos", day.seasonKey());
        assertNull(day.coverLayout());
        assertEquals("A", day.ano());
        assertEquals("Mt 26, 14 – 27, 66", day.evangelho().referencia());
        assertEquals("Paixão de Nosso Senhor Jesus Cristo", day.evangelho().citacao());
        assertEquals("Is. 50, 4-7", day.primeira().referencia());
        assertTrue(Season.resolve(day.seasonKey(), day.coverLayout()).omitsGloria());
    }

    @Test
    void aWeekdayWithoutASecondReadingOmitsThatSlide() {
        LiturgyDay day = LiturgyPage.parse(LocalDate.of(2026, 10, 19), """
                <h4>SEGUNDA-FEIRA DA XXIX SEMANA DO TEMPO COMUM</h4>
                <p>LEITURA I Ef 2, 1-10 «Deus, que é rico em misericórdia»</p>
                <p>Leitura da Epístola Irmãos.</p>
                <p>SALMO RESPONSORIAL Refrão: O Senhor é quem nos fez. Repete-se</p>
                <p>EVANGELHO Lc 12, 13-21 «A vida de um homem não depende das suas riquezas»</p>
                <p>Evangelho de Nosso Senhor Jesus Cristo segundo São Lucas Naquele tempo.</p>
                """);

        assertEquals("Segunda-feira da XXIX Semana do Tempo Comum", day.domingo());
        assertEquals("comum", day.seasonKey());
        assertNull(day.segunda());
        assertEquals("O Senhor é quem nos fez.", day.refrao());
        assertEquals("Lc 12, 13-21", day.evangelho().referencia());
    }

    @Test
    void pentecostAndAnUnknownHeading() {
        LiturgyDay pentecost = LiturgyPage.parse(LocalDate.of(2026, 5, 24), """
                <h4>DOMINGO DE PENTECOSTES</h4>
                <p>LEITURA I Atos 2, 1-11 «Todos ficaram cheios do Espírito Santo»</p>
                <p>Leitura dos Atos dos Apóstolos Quando chegou o dia.</p>
                <p>SALMO RESPONSORIAL Refrão: Enviai, Senhor, o vosso Espírito. Repete-se Ou: Aleluia. Repete-se</p>
                <p>EVANGELHO Jo 20, 19-23 «Recebei o Espírito Santo»</p>
                <p>Evangelho de Nosso Senhor Jesus Cristo segundo São João Na tarde daquele dia.</p>
                """);
        assertEquals("Domingo de Pentecostes", pentecost.domingo());
        assertEquals("pentecostes", pentecost.seasonKey());
        assertEquals("Enviai, Senhor, o vosso Espírito.", pentecost.refrao());

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> LiturgyPage.parse(
                LocalDate.of(2026, 3, 19), """
                        <h4>DOMINGO DE ALGUMA COISA</h4>
                        <p>LEITURA I Is 1, 1 «Uma frase»</p>
                        <p>SALMO RESPONSORIAL Refrão: Um refrão. Repete-se</p>
                        <p>EVANGELHO Mt 1, 1 «Outra frase»</p>
                        """));
        assertTrue(unknown.getMessage().contains("DOMINGO DE ALGUMA COISA"));
    }

    @Test
    void theCycleTurnsOnTheFirstSundayOfAdvent() {
        assertEquals(LocalDate.of(2026, 11, 29), LiturgicalYear.firstSundayOfAdvent(2026));
        assertEquals("A", LiturgicalYear.cycle(LocalDate.of(2026, 10, 18)));
        assertEquals("B", LiturgicalYear.cycle(LocalDate.of(2026, 12, 13)));
        assertEquals("C", LiturgicalYear.cycle(LocalDate.of(2025, 11, 29)));
        assertEquals("A", LiturgicalYear.cycle(LocalDate.of(2025, 11, 30)));
    }
}
