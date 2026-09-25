package br.com.conde.ordenacao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A segunda metade: juntar k blocos ordenados num só, sem carregar nenhum.
 *
 * <p>A ideia que faz isso funcionar com memória constante: se todos os blocos
 * já estão ordenados, <b>a menor linha de todas é necessariamente a primeira
 * de algum bloco</b>. Basta manter em memória uma linha de cada um — k linhas,
 * não k arquivos — e tirar sempre a menor.
 *
 * <p>Escolher a menor entre k candidatos com uma varredura custa O(k) por
 * linha. Com uma fila de prioridade custa O(log k). Para 200 blocos e 50
 * milhões de linhas, é a diferença entre dez bilhões de comparações e uma
 * fração disso.
 *
 * <p>A estabilidade é o detalhe que quase ninguém trata: linhas iguais precisam
 * sair na ordem original, e a fila de prioridade do Java <b>não é estável</b>.
 * A correção é o desempate por número do bloco, que funciona porque os blocos
 * foram gerados em ordem.
 */
public final class Intercalador {

  /** Quantos blocos intercalar de uma vez, por omissão. */
  public static final int VIAS_PADRAO = 16;

  /** Um bloco sendo lido, com a linha atual à mão. */
  private static final class Fonte implements AutoCloseable {
    private final BufferedReader leitor;
    private final int numero;
    private String atual;

    Fonte(Path arquivo, int numero) throws IOException {
      this.leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8);
      this.numero = numero;
      this.atual = leitor.readLine();
    }

    boolean acabou() {
      return atual == null;
    }

    String avancar() {
      String anterior = atual;

      try {
        atual = leitor.readLine();
      } catch (IOException erro) {
        throw new UncheckedIOException("falha lendo o bloco " + numero, erro);
      }

      return anterior;
    }

    @Override
    public void close() throws IOException {
      leitor.close();
    }
  }

  private final Comparator<String> ordem;
  private final int vias;

  public Intercalador(Comparator<String> ordem, int vias) {
    this.ordem = Objects.requireNonNull(ordem, "ordem");

    if (vias < 2) {
      throw new IllegalArgumentException("intercalar exige ao menos duas vias: " + vias);
    }

    this.vias = vias;
  }

  /**
   * Intercala blocos até sobrar um só, em quantas rodadas forem necessárias.
   *
   * <p>Com mais blocos do que vias, intercalar todos de uma vez significaria
   * abrir todos os arquivos ao mesmo tempo — e o sistema operacional tem um
   * limite. A saída é fazer em rodadas: cada rodada junta grupos de {@code
   * vias} blocos, o número de blocos cai por esse fator, e repete.
   *
   * @param blocos os blocos ordenados
   * @param saida onde gravar o resultado
   * @param pastaDeTrabalho onde deixar os blocos intermediários
   * @return quantas rodadas foram precisas
   */
  public int intercalarTudo(List<Path> blocos, Path saida, Path pastaDeTrabalho) throws IOException {
    if (blocos.isEmpty()) {
      Files.writeString(saida, "", StandardCharsets.UTF_8);

      return 0;
    }

    List<Path> atuais = new ArrayList<>(blocos);
    int rodadas = 0;
    int intermediario = 0;

    while (atuais.size() > vias) {
      List<Path> proximos = new ArrayList<>();

      for (int i = 0; i < atuais.size(); i += vias) {
        List<Path> grupo = atuais.subList(i, Math.min(i + vias, atuais.size()));

        if (grupo.size() == 1) {
          // Um bloco sozinho já está ordenado: copiar seria uma passagem de
          // disco inteira para não mudar nada.
          proximos.add(grupo.get(0));
          continue;
        }

        Path parcial = pastaDeTrabalho.resolve(String.format("juncao-%05d.txt", intermediario++));

        intercalar(grupo, parcial);
        proximos.add(parcial);
      }

      atuais = proximos;
      rodadas++;
    }

    intercalar(atuais, saida);

    return rodadas + 1;
  }

  /**
   * Intercala um grupo de blocos num arquivo só, numa passagem.
   *
   * @return quantas linhas foram escritas
   */
  public long intercalar(List<Path> blocos, Path saida) throws IOException {
    List<Fonte> fontes = new ArrayList<>(blocos.size());
    long escritas = 0;

    try {
      for (int i = 0; i < blocos.size(); i++) {
        Fonte fonte = new Fonte(blocos.get(i), i);

        // Um bloco vazio não entra na fila: ele nunca teria uma linha para
        // oferecer, e a fila passaria a comparar `null`.
        if (fonte.acabou()) {
          fonte.close();
        } else {
          fontes.add(fonte);
        }
      }

      // O desempate por número do bloco é o que torna a ordenação estável: a
      // `PriorityQueue` do Java não garante ordem entre elementos iguais, e
      // sem isto duas linhas idênticas sairiam em ordem imprevisível.
      PriorityQueue<Fonte> fila =
          new PriorityQueue<>(
              Math.max(1, fontes.size()),
              Comparator.<Fonte, String>comparing(f -> f.atual, ordem)
                  .thenComparingInt(f -> f.numero));

      fila.addAll(fontes);

      try (BufferedWriter escritor = Files.newBufferedWriter(saida, StandardCharsets.UTF_8)) {
        while (!fila.isEmpty()) {
          Fonte menor = fila.poll();

          escritor.write(menor.avancar());
          escritor.newLine();
          escritas++;

          // Reinserir é obrigatório: a chave de ordenação da fonte mudou, e
          // uma fila de prioridade não reordena sozinha quem já está dentro.
          if (!menor.acabou()) {
            fila.add(menor);
          }
        }
      }
    } finally {
      for (Fonte fonte : fontes) {
        try {
          fonte.close();
        } catch (IOException ignorado) {
          // Fechar o que der; um erro aqui não pode esconder o erro de verdade.
        }
      }
    }

    return escritas;
  }

  /** Quantos blocos este intercalador junta de uma vez. */
  public int vias() {
    return vias;
  }
}
