package br.com.conde.ordenacao;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A linha de comando, e uma demonstração que se pode rodar.
 *
 * <pre>
 *   java -cp target/classes br.com.conde.ordenacao.Principal ordenar entrada.txt saida.txt
 *   java -cp target/classes br.com.conde.ordenacao.Principal demonstrar
 * </pre>
 */
public final class Principal {

  private Principal() {}

  public static void main(String[] argumentos) throws IOException {
    if (argumentos.length == 0) {
      uso();
      System.exit(1);
    }

    switch (argumentos[0]) {
      case "ordenar" -> ordenar(argumentos);
      case "conferir" -> conferir(argumentos);
      case "demonstrar" -> demonstrar();
      default -> {
        System.err.println("comando desconhecido: " + argumentos[0]);
        uso();
        System.exit(1);
      }
    }
  }

  private static void uso() {
    System.out.println(
        """
        ordenacao-externa — ordena arquivos maiores que a memória

          ordenar <entrada> <saida> [linhas-por-bloco] [vias]
          conferir <arquivo>
          demonstrar
        """);
  }

  private static void ordenar(String[] argumentos) throws IOException {
    if (argumentos.length < 3) {
      System.err.println("uso: ordenar <entrada> <saida> [linhas-por-bloco] [vias]");
      System.exit(1);
    }

    Path entrada = Path.of(argumentos[1]);
    Path saida = Path.of(argumentos[2]);

    int porBloco = argumentos.length > 3 ? Integer.parseInt(argumentos[3]) : Blocos.LINHAS_POR_BLOCO;
    int vias = argumentos.length > 4 ? Integer.parseInt(argumentos[4]) : Intercalador.VIAS_PADRAO;

    Path trabalho = Files.createTempDirectory("ordenacao-");
    long comeco = System.nanoTime();

    var relatorio =
        new OrdenacaoExterna(Comparator.naturalOrder(), porBloco, vias)
            .ordenar(entrada, saida, trabalho);

    long ms = (System.nanoTime() - comeco) / 1_000_000;

    Files.deleteIfExists(trabalho);

    System.out.printf(
        "%d linhas | %d blocos | %d rodada(s) | %d MB de temporários | %d ms%n",
        relatorio.linhas(),
        relatorio.blocos(),
        relatorio.rodadas(),
        relatorio.bytesIntermediarios() / (1024 * 1024),
        ms);
  }

  private static void conferir(String[] argumentos) throws IOException {
    if (argumentos.length < 2) {
      System.err.println("uso: conferir <arquivo>");
      System.exit(1);
    }

    boolean ordenado = OrdenacaoExterna.estaOrdenado(Path.of(argumentos[1]), Comparator.naturalOrder());

    System.out.println(ordenado ? "ordenado" : "FORA DE ORDEM");
    System.exit(ordenado ? 0 : 1);
  }

  /** Gera um arquivo, ordena e confere contra a ordenação em memória. */
  private static void demonstrar() throws IOException {
    Path pasta = Files.createTempDirectory("demonstracao-");
    Path entrada = pasta.resolve("entrada.txt");
    Path saida = pasta.resolve("saida.txt");

    final int quantas = 2_000_000;
    Random sorteio = new Random(20260925);

    System.out.printf("Gerando %,d linhas...%n", quantas);

    try (BufferedWriter escritor = Files.newBufferedWriter(entrada, StandardCharsets.UTF_8)) {
      for (int i = 0; i < quantas; i++) {
        escritor.write("chave-" + sorteio.nextInt(1_000_000) + " registro " + i);
        escritor.newLine();
      }
    }

    long tamanho = Files.size(entrada);

    System.out.printf("Arquivo: %,d MB%n%n", tamanho / (1024 * 1024));

    for (int porBloco : new int[] {50_000, 200_000}) {
      long comeco = System.nanoTime();

      var relatorio =
          new OrdenacaoExterna(Comparator.naturalOrder(), porBloco, Intercalador.VIAS_PADRAO)
              .ordenar(entrada, saida, pasta.resolve("trabalho-" + porBloco));

      long ms = (System.nanoTime() - comeco) / 1_000_000;

      System.out.printf(
          "  bloco de %,d linhas: %d blocos, %d rodada(s), %,d ms%n",
          porBloco, relatorio.blocos(), relatorio.rodadas(), ms);
    }

    System.out.println("\nConferindo contra a ordenação em memória...");

    List<String> tudo = new ArrayList<>(Files.readAllLines(entrada, StandardCharsets.UTF_8));

    tudo.sort(Comparator.naturalOrder());

    boolean igual = tudo.equals(Files.readAllLines(saida, StandardCharsets.UTF_8));

    System.out.println(igual ? "  igual, linha a linha." : "  DIVERGIU.");

    apagarTudo(pasta);
  }

  private static void apagarTudo(Path pasta) throws IOException {
    try (var fluxo = Files.walk(pasta)) {
      for (Path caminho : fluxo.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(caminho);
      }
    }
  }
}
