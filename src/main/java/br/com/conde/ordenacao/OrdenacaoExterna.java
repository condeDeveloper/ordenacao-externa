package br.com.conde.ordenacao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ordena um arquivo maior que a memória.
 *
 * <p>Duas fases, e nenhuma delas carrega o arquivo inteiro:
 *
 * <ol>
 *   <li><b>Gerar blocos</b> — lê o quanto cabe, ordena em memória, grava. Ver
 *       {@link Blocos}.
 *   <li><b>Intercalar</b> — junta os blocos mantendo uma linha de cada em
 *       memória. Ver {@link Intercalador}.
 * </ol>
 *
 * <p>O custo é de duas leituras e duas escritas do arquivo quando os blocos
 * cabem numa rodada de intercalação só. Cada rodada extra soma mais uma leitura
 * e uma escrita — daí a conta que decide o tamanho do bloco valer a pena:
 * blocos maiores gastam mais memória e geram menos blocos, e menos blocos
 * significam menos rodadas.
 */
public final class OrdenacaoExterna {

  /** O relatório do que aconteceu, para quem quiser medir em vez de supor. */
  public record Relatorio(long linhas, int blocos, int rodadas, long bytesIntermediarios) {}

  private final Comparator<String> ordem;
  private final int linhasPorBloco;
  private final int vias;

  public OrdenacaoExterna() {
    this(Comparator.naturalOrder(), Blocos.LINHAS_POR_BLOCO, Intercalador.VIAS_PADRAO);
  }

  public OrdenacaoExterna(Comparator<String> ordem, int linhasPorBloco, int vias) {
    this.ordem = Objects.requireNonNull(ordem, "ordem");
    this.linhasPorBloco = linhasPorBloco;
    this.vias = vias;
  }

  /**
   * Ordena {@code entrada} e grava o resultado em {@code saida}.
   *
   * @param pastaDeTrabalho onde os blocos temporários ficam; precisa caber o
   *     arquivo inteiro, porque é isso que a ordenação externa troca por
   *     memória
   */
  public Relatorio ordenar(Path entrada, Path saida, Path pastaDeTrabalho) throws IOException {
    Objects.requireNonNull(entrada, "entrada");
    Objects.requireNonNull(saida, "saida");

    Files.createDirectories(pastaDeTrabalho);

    Blocos geradorDeBlocos = new Blocos(pastaDeTrabalho, ordem, linhasPorBloco);
    List<Path> blocos = geradorDeBlocos.gerar(entrada);

    long bytes = 0;

    for (Path bloco : blocos) {
      bytes += Files.size(bloco);
    }

    int rodadas = new Intercalador(ordem, vias).intercalarTudo(blocos, saida, pastaDeTrabalho);

    long linhas = 0;

    try (var fluxo = Files.lines(saida)) {
      linhas = fluxo.count();
    }

    apagarTemporarios(pastaDeTrabalho, saida);

    return new Relatorio(linhas, blocos.size(), rodadas, bytes);
  }

  private void apagarTemporarios(Path pastaDeTrabalho, Path saida) throws IOException {
    try (var fluxo = Files.list(pastaDeTrabalho)) {
      for (Path arquivo : fluxo.toList()) {
        String nome = arquivo.getFileName().toString();

        // A saída pode estar na mesma pasta de trabalho; apagá-la seria
        // entregar um arquivo vazio depois de todo o trabalho.
        if (arquivo.equals(saida)) {
          continue;
        }

        if (nome.startsWith("bloco-") || nome.startsWith("juncao-")) {
          Files.deleteIfExists(arquivo);
        }
      }
    }
  }

  /** Se um arquivo está ordenado, sem carregá-lo na memória. */
  public static boolean estaOrdenado(Path arquivo, Comparator<String> ordem) throws IOException {
    try (var leitor = Files.newBufferedReader(arquivo)) {
      String anterior = null;
      String linha;

      while ((linha = leitor.readLine()) != null) {
        if (anterior != null && ordem.compare(anterior, linha) > 0) {
          return false;
        }

        anterior = linha;
      }
    }

    return true;
  }
}
