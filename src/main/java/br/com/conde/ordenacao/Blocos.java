package br.com.conde.ordenacao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A primeira metade da ordenação externa: partir a entrada em blocos ordenados.
 *
 * <p>O problema que a ordenação externa resolve não é de algoritmo, é de
 * hardware: <b>o arquivo não cabe na memória</b>. Um {@code Collections.sort}
 * num arquivo de 50 GB não é lento, é impossível.
 *
 * <p>A saída é a mesma de sempre — divide e conquista —, só que a divisão é
 * ditada pela memória disponível e as partes vão para o disco. Lê-se o quanto
 * couber, ordena-se em memória, grava-se um bloco ordenado, repete. Depois os
 * blocos são intercalados (ver {@link Intercalador}).
 *
 * <p>O custo total é <b>duas leituras e duas escritas</b> do arquivo inteiro
 * quando os blocos cabem todos numa rodada de intercalação. É por isso que o
 * número de blocos importa mais do que parece: passar do limite de arquivos
 * abertos obriga a uma segunda rodada, e aí são quatro passagens.
 */
public final class Blocos {

  /** Quantas linhas cabem num bloco, por omissão. */
  public static final int LINHAS_POR_BLOCO = 100_000;

  private final Path pastaDeTrabalho;
  private final Comparator<String> ordem;
  private final int linhasPorBloco;

  public Blocos(Path pastaDeTrabalho, Comparator<String> ordem, int linhasPorBloco) {
    this.pastaDeTrabalho = Objects.requireNonNull(pastaDeTrabalho, "pastaDeTrabalho");
    this.ordem = Objects.requireNonNull(ordem, "ordem");

    if (linhasPorBloco <= 0) {
      throw new IllegalArgumentException("o bloco precisa de ao menos uma linha: " + linhasPorBloco);
    }

    this.linhasPorBloco = linhasPorBloco;
  }

  /**
   * Lê a entrada e grava blocos ordenados.
   *
   * @param entrada o arquivo a ordenar
   * @return os blocos gerados, na ordem em que saíram
   */
  public List<Path> gerar(Path entrada) throws IOException {
    List<Path> blocos = new ArrayList<>();

    try (BufferedReader leitor = Files.newBufferedReader(entrada, StandardCharsets.UTF_8)) {
      List<String> lote = new ArrayList<>(Math.min(linhasPorBloco, 4096));
      String linha;

      while ((linha = leitor.readLine()) != null) {
        lote.add(linha);

        if (lote.size() >= linhasPorBloco) {
          blocos.add(gravar(lote, blocos.size()));
          lote.clear();
        }
      }

      // O último lote quase nunca é cheio, e esquecê-lo é o jeito mais fácil
      // de perder o fim do arquivo sem que nada reclame.
      if (!lote.isEmpty()) {
        blocos.add(gravar(lote, blocos.size()));
      }
    }

    return blocos;
  }

  private Path gravar(List<String> lote, int numero) throws IOException {
    lote.sort(ordem);

    Path bloco = pastaDeTrabalho.resolve(String.format("bloco-%05d.txt", numero));

    try (BufferedWriter escritor = Files.newBufferedWriter(bloco, StandardCharsets.UTF_8)) {
      for (String linha : lote) {
        escritor.write(linha);
        escritor.newLine();
      }
    }

    return bloco;
  }

  /** Quantas linhas cabem em cada bloco. */
  public int linhasPorBloco() {
    return linhasPorBloco;
  }
}
