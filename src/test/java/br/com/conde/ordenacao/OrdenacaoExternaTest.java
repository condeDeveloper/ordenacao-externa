package br.com.conde.ordenacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O oráculo é a ordenação em memória.
 *
 * <p>Para um arquivo que cabe na memória, {@code List.sort} dá a resposta certa
 * por construção — é a mesma pergunta respondida do jeito fácil. A ordenação
 * externa tem de chegar **exatamente** ao mesmo resultado, linha a linha.
 *
 * <p>O truque dos testes é forçar o caso difícil sem precisar de um arquivo de
 * 50 GB: basta pedir blocos minúsculos. Um bloco de 3 linhas num arquivo de
 * 5.000 gera 1.667 blocos e obriga várias rodadas de intercalação — o mesmo
 * caminho de código que um arquivo gigante percorreria.
 */
class OrdenacaoExternaTest {

  private static List<String> linhasSorteadas(long semente, int quantas) {
    Random sorteio = new Random(semente);
    List<String> linhas = new ArrayList<>(quantas);

    for (int i = 0; i < quantas; i++) {
      linhas.add("chave-" + sorteio.nextInt(quantas * 2) + " valor " + i);
    }

    return linhas;
  }

  private static Path escrever(Path arquivo, List<String> linhas) throws IOException {
    Files.write(arquivo, linhas, StandardCharsets.UTF_8);

    return arquivo;
  }

  @Test
  @DisplayName("bate com a ordenação em memória, linha a linha")
  void bateComAMemoria(@TempDir Path pasta) throws IOException {
    List<String> linhas = linhasSorteadas(1, 5_000);
    Path entrada = escrever(pasta.resolve("entrada.txt"), linhas);
    Path saida = pasta.resolve("saida.txt");

    // Blocos de 50 linhas: 100 blocos, mais que as 16 vias, então há rodadas.
    new OrdenacaoExterna(Comparator.naturalOrder(), 50, 16)
        .ordenar(entrada, saida, pasta.resolve("trabalho"));

    List<String> esperado = new ArrayList<>(linhas);

    esperado.sort(Comparator.naturalOrder());

    assertEquals(esperado, Files.readAllLines(saida, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("blocos minúsculos forçam várias rodadas e o resultado continua certo")
  void variasRodadas(@TempDir Path pasta) throws IOException {
    List<String> linhas = linhasSorteadas(2, 2_000);
    Path entrada = escrever(pasta.resolve("entrada.txt"), linhas);
    Path saida = pasta.resolve("saida.txt");

    // 3 linhas por bloco e 4 vias: 667 blocos, e log4(667) ≈ 5 rodadas.
    var relatorio =
        new OrdenacaoExterna(Comparator.naturalOrder(), 3, 4)
            .ordenar(entrada, saida, pasta.resolve("trabalho"));

    assertTrue(relatorio.blocos() > 600, "o teste precisa mesmo gerar muitos blocos");
    assertTrue(relatorio.rodadas() >= 4, "e precisa mesmo de várias rodadas: " + relatorio.rodadas());

    List<String> esperado = new ArrayList<>(linhas);

    esperado.sort(Comparator.naturalOrder());

    assertEquals(esperado, Files.readAllLines(saida, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("nenhuma linha se perde nem aparece do nada")
  void nadaSePerde(@TempDir Path pasta) throws IOException {
    // O defeito mais fácil de cometer aqui é esquecer o último lote, que quase
    // nunca é cheio — e o arquivo sai quase certo, faltando o fim.
    for (int quantas : new int[] {0, 1, 2, 49, 50, 51, 99, 100, 101}) {
      Path propria = Files.createDirectories(pasta.resolve("n" + quantas));
      List<String> linhas = linhasSorteadas(quantas + 7L, quantas);
      Path entrada = escrever(propria.resolve("entrada.txt"), linhas);
      Path saida = propria.resolve("saida.txt");

      var relatorio =
          new OrdenacaoExterna(Comparator.naturalOrder(), 10, 4)
              .ordenar(entrada, saida, propria.resolve("trabalho"));

      assertEquals(quantas, relatorio.linhas(), "com " + quantas + " linhas");
      assertEquals(quantas, Files.readAllLines(saida, StandardCharsets.UTF_8).size());
    }
  }

  @Test
  @DisplayName("a ordenação é estável: linhas iguais mantêm a ordem original")
  void estavel(@TempDir Path pasta) throws IOException {
    // A `PriorityQueue` do Java não é estável, e sem o desempate por bloco duas
    // linhas com a mesma chave sairiam em ordem imprevisível. Aqui a chave é só
    // a primeira palavra, então o resto distingue as linhas e denuncia a troca.
    List<String> linhas = new ArrayList<>();

    for (int i = 0; i < 300; i++) {
      linhas.add("chave" + (i % 5) + " ocorrencia " + String.format("%04d", i));
    }

    Path entrada = escrever(pasta.resolve("entrada.txt"), linhas);
    Path saida = pasta.resolve("saida.txt");

    Comparator<String> porPrimeiraPalavra =
        Comparator.comparing(linha -> linha.substring(0, linha.indexOf(' ')));

    new OrdenacaoExterna(porPrimeiraPalavra, 7, 3)
        .ordenar(entrada, saida, pasta.resolve("trabalho"));

    List<String> esperado = new ArrayList<>(linhas);

    esperado.sort(porPrimeiraPalavra);

    assertEquals(esperado, Files.readAllLines(saida, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("aceita uma ordem própria, inclusive numérica")
  void ordemPropria(@TempDir Path pasta) throws IOException {
    // Ordem alfabética põe "10" antes de "9". Quando isso importa, a ordem é
    // outra — e o algoritmo não pode ter opinião sobre qual.
    List<String> linhas = new ArrayList<>();
    Random sorteio = new Random(9);

    for (int i = 0; i < 1_000; i++) {
      linhas.add(String.valueOf(sorteio.nextInt(100_000)));
    }

    Path entrada = escrever(pasta.resolve("entrada.txt"), linhas);
    Path saida = pasta.resolve("saida.txt");

    Comparator<String> numerica = Comparator.comparingLong(Long::parseLong);

    new OrdenacaoExterna(numerica, 40, 5).ordenar(entrada, saida, pasta.resolve("trabalho"));

    List<String> esperado = new ArrayList<>(linhas);

    esperado.sort(numerica);

    assertEquals(esperado, Files.readAllLines(saida, StandardCharsets.UTF_8));
    assertTrue(OrdenacaoExterna.estaOrdenado(saida, numerica));
  }

  @Test
  @DisplayName("já ordenado continua ordenado, e invertido também sai certo")
  void casosExtremos(@TempDir Path pasta) throws IOException {
    List<String> crescente = new ArrayList<>();

    for (int i = 0; i < 500; i++) {
      crescente.add(String.format("linha-%04d", i));
    }

    List<String> decrescente = new ArrayList<>(crescente);

    java.util.Collections.reverse(decrescente);

    for (var caso : List.of(crescente, decrescente)) {
      Path propria = Files.createTempDirectory(pasta, "caso");
      Path entrada = escrever(propria.resolve("entrada.txt"), caso);
      Path saida = propria.resolve("saida.txt");

      new OrdenacaoExterna(Comparator.naturalOrder(), 25, 4)
          .ordenar(entrada, saida, propria.resolve("trabalho"));

      assertEquals(crescente, Files.readAllLines(saida, StandardCharsets.UTF_8));
    }
  }

  @Test
  @DisplayName("linhas todas iguais não confundem a fila")
  void tudoIgual(@TempDir Path pasta) throws IOException {
    List<String> linhas = new ArrayList<>(java.util.Collections.nCopies(500, "mesma linha"));
    Path entrada = escrever(pasta.resolve("entrada.txt"), linhas);
    Path saida = pasta.resolve("saida.txt");

    var relatorio =
        new OrdenacaoExterna(Comparator.naturalOrder(), 20, 3)
            .ordenar(entrada, saida, pasta.resolve("trabalho"));

    assertEquals(500, relatorio.linhas());
    assertEquals(linhas, Files.readAllLines(saida, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("os arquivos temporários somem no fim")
  void limpaOsTemporarios(@TempDir Path pasta) throws IOException {
    Path trabalho = pasta.resolve("trabalho");
    Path entrada = escrever(pasta.resolve("entrada.txt"), linhasSorteadas(3, 500));

    new OrdenacaoExterna(Comparator.naturalOrder(), 10, 4)
        .ordenar(entrada, pasta.resolve("saida.txt"), trabalho);

    try (var fluxo = Files.list(trabalho)) {
      assertEquals(List.of(), fluxo.toList(), "sobrou bloco temporário na pasta de trabalho");
    }
  }

  @Test
  @DisplayName("a saída pode ficar na própria pasta de trabalho sem ser apagada")
  void saidaDentroDaPastaDeTrabalho(@TempDir Path pasta) throws IOException {
    Path trabalho = Files.createDirectories(pasta.resolve("trabalho"));
    Path entrada = escrever(pasta.resolve("entrada.txt"), linhasSorteadas(4, 300));
    Path saida = trabalho.resolve("saida.txt");

    new OrdenacaoExterna(Comparator.naturalOrder(), 10, 4).ordenar(entrada, saida, trabalho);

    assertEquals(300, Files.readAllLines(saida, StandardCharsets.UTF_8).size());
  }

  @Test
  @DisplayName("parâmetros impossíveis são recusados na porta")
  void parametrosInvalidos(@TempDir Path pasta) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Blocos(pasta, Comparator.naturalOrder(), 0));

    assertThrows(
        IllegalArgumentException.class, () -> new Intercalador(Comparator.naturalOrder(), 1));
  }

  @Test
  @DisplayName("estaOrdenado percebe quem não está")
  void conferirOrdem(@TempDir Path pasta) throws IOException {
    Path bom = escrever(pasta.resolve("bom.txt"), List.of("a", "b", "c"));
    Path ruim = escrever(pasta.resolve("ruim.txt"), List.of("a", "c", "b"));

    assertTrue(OrdenacaoExterna.estaOrdenado(bom, Comparator.naturalOrder()));
    assertFalse(OrdenacaoExterna.estaOrdenado(ruim, Comparator.naturalOrder()));
  }

  @Test
  @DisplayName("cinquenta arquivos sorteados, com tamanhos e parâmetros variados")
  void muitosSorteados(@TempDir Path pasta) throws IOException {
    Random sorteio = new Random(20260925);

    for (int rodada = 0; rodada < 50; rodada++) {
      int quantas = sorteio.nextInt(400);
      int porBloco = 1 + sorteio.nextInt(30);
      int vias = 2 + sorteio.nextInt(6);

      Path propria = Files.createTempDirectory(pasta, "r" + rodada);
      List<String> linhas = linhasSorteadas(rodada, quantas);
      Path entrada = escrever(propria.resolve("entrada.txt"), linhas);
      Path saida = propria.resolve("saida.txt");

      new OrdenacaoExterna(Comparator.naturalOrder(), porBloco, vias)
          .ordenar(entrada, saida, propria.resolve("trabalho"));

      List<String> esperado = new ArrayList<>(linhas);

      esperado.sort(Comparator.naturalOrder());

      assertEquals(
          esperado,
          Files.readAllLines(saida, StandardCharsets.UTF_8),
          "rodada " + rodada + ": " + quantas + " linhas, bloco " + porBloco + ", " + vias + " vias");
    }
  }
}
