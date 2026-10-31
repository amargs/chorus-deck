package org.amargs.chorusdeck.deck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EstrofesTest {

    @Test
    void anEstrofeOwnsItsOwnTextBoxes() {
        Estrofes.Plan bomPastor = Estrofes.plan(List.of(
                "Quando Te encontro\n\n**Cristo, Senhor**",
                "O Teu festim\n\n**Cristo, Senhor**",
                "Tua bondade\n\n**Cristo, Senhor**"), "192");
        assertEquals("Cristo, Senhor", bomPastor.refrain());
        assertEquals("no fim de cada estrofe", bomPastor.where());
        assertEquals(List.of(1), bomPastor.rows().get(0).slides());
        assertEquals(List.of(2), bomPastor.rows().get(1).slides());
        assertEquals(List.of(3), bomPastor.rows().get(2).slides());
        assertEquals("Quando Te encontro", bomPastor.rows().get(0).line());

        Estrofes.Plan caminho = Estrofes.plan(List.of(
                "Aqui estamos nós, junto a Ti\nTu nos convidaste a vir aqui",
                "**O caminho**",
                "Testemunha fiel da verdade\nE da justiça defensor",
                "**O caminho**"), "20");
        assertEquals("Aqui estamos nós, junto a Ti Tu nos", caminho.rows().get(0).line());
        assertEquals("Testemunha fiel da verdade E da", caminho.rows().get(1).line());

        Estrofes.Plan aleluia = Estrofes.plan(List.of(
                "**Aleluia, aleluia!**",
                "Escutai, amigos",
                "**Aleluia, aleluia!**",
                "Glória a Deus",
                "**Aleluia, aleluia!**",
                "Deus enviou o Seu Filho",
                "**Aleluia, aleluia!**"), "10");
        assertEquals("no início e depois de cada estrofe", aleluia.where());
        assertEquals(List.of(1, 2, 3), aleluia.rows().get(0).slides());
        assertEquals(List.of(4, 5), aleluia.rows().get(1).slides());
        assertEquals(List.of(6, 7), aleluia.rows().get(2).slides());

        Estrofes.Plan paired = Estrofes.plan(List.of(
                "Eu o Deus do céu",
                "Eu criei as estrelas",
                "**Aqui estou eu**",
                "Eu o Deus da chuva",
                "Quebrarei toda a pedra",
                "**Aqui estou eu**"), "13");
        assertEquals("depois da 2.ª e da 4.ª", paired.where());
        assertEquals(List.of(1), paired.rows().get(0).slides());
        assertEquals(List.of(2, 3), paired.rows().get(1).slides());
        assertEquals(List.of(4), paired.rows().get(2).slides());
        assertEquals(List.of(5, 6), paired.rows().get(3).slides());

        Estrofes.Plan preChorus = Estrofes.plan(List.of(
                "O amor que o Espírito me dá",
                "*Sol da minha vida*",
                "**Vem Espírito de amor**",
                "O amor que desceu do céu",
                "*Sinto a Tua força*",
                "**Vem Espírito de amor**"), "178");
        assertEquals(List.of(1, 2, 3), preChorus.rows().get(0).slides());
        assertEquals(List.of(4, 5, 6), preChorus.rows().get(1).slides());
        assertEquals("depois de cada estrofe", preChorus.where());
    }

    @Test
    void aSongWithoutARefrainOrARiteHasNoChoice() {
        assertNull(Estrofes.plan(List.of("um", "dois"), "12"));
        assertNull(Estrofes.plan(List.of("**só**", "**isto**"), "7"));
        assertNull(Estrofes.plan(List.of("verso", "**refrão**"), "4"));
        assertNull(Estrofes.plan(List.of(
                "**Aleluia**",
                "Natal",
                "**Aleluia**",
                "Páscoa",
                "**Aleluia**"), "A5"));
    }
}
