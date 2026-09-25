# ordenacao-externa

Ordenar um arquivo **maior que a memória**, do zero em Java 21. Sem dependência
de produção — só JUnit para testar.

O problema não é de algoritmo, é de hardware: um `Collections.sort` num arquivo
de 50 GB não é lento, é impossível. A saída é a de sempre — divide e conquista
—, só que a divisão é ditada pela memória disponível e as partes vão para o
disco.

```
$ java -Xmx512m -cp target/classes br.com.conde.ordenacao.Principal demonstrar
Gerando 2.000.000 linhas...
Arquivo: 57 MB

  bloco de 50.000 linhas: 40 blocos, 2 rodada(s), 5.119 ms
  bloco de 200.000 linhas: 10 blocos, 1 rodada(s), 7.732 ms

Conferindo contra a ordenação em memória...
  igual, linha a linha.
```

## As duas fases

**1. Gerar blocos.** Lê o quanto cabe na memória, ordena ali mesmo, grava um
bloco ordenado no disco. Repete até acabar a entrada.

**2. Intercalar.** Junta os blocos num arquivo só. A ideia que faz isso caber
na memória: se todos os blocos já estão ordenados, **a menor linha de todas é
necessariamente a primeira de algum bloco**. Basta manter uma linha de cada em
memória — k linhas, não k arquivos.

Escolher a menor entre k candidatos com uma varredura custa O(k) por linha; com
uma fila de prioridade custa O(log k). Para 200 blocos e 50 milhões de linhas,
é a diferença entre dez bilhões de comparações e uma fração disso.

## O resultado que contrariou a expectativa

A intuição diz: menos blocos → menos rodadas de intercalação → mais rápido. A
medição diz o contrário.

| linhas por bloco | blocos | rodadas | tempo |
|---|---|---|---|
| 50.000 | 40 | **2** | **5.119 ms** |
| 200.000 | 10 | **1** | 7.732 ms |

Blocos quatro vezes maiores cortaram uma rodada inteira de leitura e escrita —
e mesmo assim ficaram **50% mais lentos**. O motivo é que a ordenação em
memória de cada bloco é n·log n, e quadruplicar n custa mais do que a rodada
economizada; ainda por cima, blocos maiores pressionam mais o coletor de lixo.

Não é uma regra universal — com disco lento a conta inverte, porque aí a
passagem de E/S domina. É um lembrete de que "menos passagens de disco" é uma
heurística, não um teorema, e que o tamanho do bloco merece ser medido em vez
de escolhido por bom senso.

## O oráculo

Para um arquivo que cabe na memória, `List.sort` dá a resposta certa por
construção — é a mesma pergunta respondida do jeito fácil. A ordenação externa
tem de chegar **exatamente** ao mesmo resultado, linha a linha.

O truque dos testes é forçar o caso difícil sem precisar de um arquivo de 50
GB: **basta pedir blocos minúsculos**. Três linhas por bloco num arquivo de
2.000 gera 667 blocos e obriga cinco rodadas de intercalação — o mesmo caminho
de código que um arquivo gigante percorreria, rodando em milissegundos.

Cinquenta arquivos sorteados, com tamanho, tamanho de bloco e número de vias
variados, passam pelos dois caminhos e têm de bater.

## Detalhes que decidem se está certo

**A fila de prioridade do Java não é estável.** Linhas iguais sairiam em ordem
imprevisível. A correção é desempatar pelo número do bloco, que funciona porque
os blocos são gerados em ordem — e um teste ordena 300 linhas por uma chave
que repete a cada 5 para pegar isso.

**Reinserir na fila é obrigatório.** Depois de consumir uma linha, a chave de
ordenação daquela fonte mudou, e uma fila de prioridade não reordena sozinha
quem já está dentro.

**O último lote quase nunca é cheio.** Esquecê-lo é o jeito mais fácil de
perder o fim do arquivo sem que nada reclame — o resultado sai ordenado, só que
incompleto. Um teste roda com 0, 1, 2, 49, 50, 51, 99, 100 e 101 linhas para
cercar as bordas do bloco de 10.

**Bloco vazio não entra na fila.** Ele nunca teria uma linha a oferecer, e a
fila passaria a comparar `null`.

**Grupo de um bloco só não é intercalado.** Ele já está ordenado; copiá-lo
seria uma passagem de disco inteira para não mudar nada.

**A saída pode estar na pasta de trabalho.** A limpeza dos temporários precisa
saber disso, ou entrega um arquivo vazio depois de todo o trabalho.

## Rodando

```bash
mvn test

mvn -q compile
java -cp target/classes br.com.conde.ordenacao.Principal ordenar entrada.txt saida.txt
java -cp target/classes br.com.conde.ordenacao.Principal ordenar entrada.txt saida.txt 50000 16
java -cp target/classes br.com.conde.ordenacao.Principal conferir saida.txt
java -Xmx512m -cp target/classes br.com.conde.ordenacao.Principal demonstrar
```

Como biblioteca:

```java
var relatorio = new OrdenacaoExterna(Comparator.naturalOrder(), 100_000, 16)
    .ordenar(Path.of("grande.txt"), Path.of("ordenado.txt"), Path.of("/tmp/trabalho"));

relatorio.blocos();   // quantos blocos foram gerados
relatorio.rodadas();  // quantas passagens de intercalação
```

12 testes, cobrindo as bordas do bloco, a estabilidade, as várias rodadas, a
ordem própria, o já-ordenado, o invertido, o tudo-igual e a limpeza dos
temporários.

## Estrutura

```
Blocos.java             fase 1: lê o que cabe, ordena, grava
Intercalador.java       fase 2: k vias com fila de prioridade, em rodadas
OrdenacaoExterna.java   junta as duas e limpa a sujeira
Principal.java          linha de comando e a demonstração acima
```

## Limites conhecidos

- **O disco precisa caber o arquivo duas vezes.** É exatamente o que a
  ordenação externa troca por memória, e não há como contornar: os blocos e a
  saída coexistem.
- **Uma linha precisa caber na memória.** O algoritmo nunca carrega o arquivo,
  mas carrega uma linha por bloco — um arquivo de uma linha de 50 GB derruba
  tudo.
- **Sem paralelismo.** Gerar blocos é embaraçosamente paralelo e aqui é
  sequencial. Ordenar quatro blocos em quatro núcleos seria quase quatro vezes
  mais rápido na fase 1.
- **Sem seleção por substituição.** A técnica clássica gera blocos com o dobro
  do tamanho em média, mantendo um heap enquanto lê. Vale menos hoje do que em
  1970 — memória é barata e blocos grandes já são a norma —, mas é a otimização
  óbvia que falta.
- **Só texto, linha a linha, em UTF-8.** Nada de registros binários de tamanho
  fixo, que é como bancos de dados fazem de verdade.
- **Sem remoção de duplicatas.** O `sort -u` faz; aqui todas as linhas saem.

## Onde ele se encaixa

Faz par com a [`arvore-b`](https://github.com/condeDeveloper/arvore-b), que é a
outra metade de como um banco de dados lida com dados maiores que a memória:
uma indexa para achar rápido, a outra ordena para varrer em ordem. As duas
partem da mesma restrição — o disco é lento e a memória é pequena — e chegam a
estruturas completamente diferentes.

## Licença

MIT.
