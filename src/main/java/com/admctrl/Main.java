package com.admctrl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        //в задании ничего не сказано про обработку ошибок при не корректном использовании
        //также можно бросить RuntimeException для аварийного завершения через необработанное исключение
        if (args.length != 1) {                  //если запущено без аргументов, или несколькими
            System.exit(1);              //общая ошибка
        }

        Path path = Paths.get(args[0]);
        if (!Files.exists(path)) {                //если не найден файл
            System.exit(2);               //аварийное завершение с пользовательским кодом. Файл не найден
        }

        //финализируем коллекции для использования в лямбда-выражениях
        final List<String[]> tokenizedLines = new ArrayList<>(1_000_000); //за счет ресайза неизбежны потери
        final Map<String, String> tokenPool = new HashMap<>();

        try (Stream<String> lines = Files.lines(path)) {         //пытаемся считать в стрим файл построчно
            lines.filter(Main::validateLine)                     //фильтруем некорректные строки
                    .forEach(line -> {
                        String[] tokens = line.split(";");
                        String[] cleanedTokens = new String[tokens.length];

                        //обработка токенов - удаление кавычек и дедупликация
                        for (int i = 0; i < tokens.length; i++) {
                            String token = tokens[i];
                            //удаляем обрамляющие кавычки
                            if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
                                token = token.substring(1, token.length() - 1);
                            }
                            //дедупликация токенов через пул, но только для непустых значений
                            if (!token.isEmpty()) {
                                String uniqueToken = tokenPool.get(token);
                                if (uniqueToken == null) {
                                    tokenPool.put(token, token);
                                    uniqueToken = token;
                                }
                                cleanedTokens[i] = uniqueToken;
                            } else {
                                cleanedTokens[i] = "";                //сохраняем пустую строку как есть
                            }
                        }
                        tokenizedLines.add(cleanedTokens);
                    });
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
            System.exit(3);
        }

        System.out.printf("Всего корректных строк в фале: %s\n", tokenizedLines.size());

        // Находим максимальное количество столбцов
        int maxColumns = tokenizedLines.stream()
                .mapToInt(tokens -> tokens.length)
                .max()
                .orElse(0);

        //инициализируем DSU структуру с количеством строк
        UnionFind uf = new UnionFind(tokenizedLines.size());

        //обработка столбцов для объединения строк
        //используем финальную переменную для lastSeenMap
        IntStream.range(0, maxColumns).forEach(col -> {         //убрал parallel(), он точно небезопасен. если оба потока одновременно запишут parent[x] = y и parent[y] = x - то гонка
            final Map<String, Integer> lastSeenMap = new HashMap<>();
            for (int id = 0; id < tokenizedLines.size(); id++) {
                String[] tokens = tokenizedLines.get(id);
                if (col >= tokens.length) continue;             //пропускаем если столбца нет в строке

                String value = tokens[col];
                //пропускаем пустые значения при объединении, они не участвуют в группировке
                if (value == null || value.isEmpty()) continue;

                //объединяем с предыдущей строкой если такое же значение
                if (lastSeenMap.containsKey(value)) {
                    int otherId = lastSeenMap.get(value);
                    if (uf.find(id) != uf.find(otherId)) { //если uf.union() вызывается тысячи или миллионы раз на уже объединённые элементы, тогда
                        uf.union(id, otherId);             // при плохом порядке обхода возможно попадание в "цикл", да и find() будет вызываться без пользы
                    }
                } else {
                    lastSeenMap.put(value, id);
                }
            }
        });

        //группируем строки на основе их представителей DSU
        Map<Integer, List<Integer>> groupedLines = new HashMap<>();
        for (int i = 0; i < tokenizedLines.size(); i++) {
            int root = uf.find(i);
            groupedLines.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        //создаем список с информацией о группах для последующей сортировки
        List<GroupInfo> groupInfos = new ArrayList<>();
        for (List<Integer> group : groupedLines.values()) {
            //группа должна содержать более одной строки
            if (group.size() > 1) {
                int totalElements = 0;                          //счетчик непустых элементов в группе
                int minId = Integer.MAX_VALUE;                  //для определения минимального ID в группе
/*
        подсчитываем только непустые элементы в группе.
        Взял на себя смелость предположить, что пустые значения не должны учитываться при подсчете общего количества
        элементов в группе, так как они не участвуют в объединении строк, не влияют на принадлежность строки к группе,
        и не являются значимыми данными. В условии задачи нет определения пустого значения "", следует ли считать это
        элементом группы
*/
                for (int id : group) {
                    String[] tokens = tokenizedLines.get(id);
                    for (String token : tokens) {
                        //учитываем только непустые значения
                        if (token != null && !token.isEmpty()) {
                            totalElements++;
                        }
                    }
                    //обновляем минимальный ID для группы
                    if (id < minId) minId = id;
                }
                groupInfos.add(new GroupInfo(group, totalElements, minId));
            }
        }

        //сортируем группы по общему количеству НЕПУСТЫХ элементов (убывание), затем по минимальному ID (возрастание)
        groupInfos.sort((g1, g2) -> {
            //сначала по общему количеству элементов (убывание)
            int totalCompare = Integer.compare(g2.totalElements, g1.totalElements);
            if (totalCompare != 0) {
                return totalCompare;
            }
            //при равном количестве элементов сортируем по минимальному ID (возрастание)
            return Integer.compare(g1.minId, g2.minId);
        });

        //преобразуем обратно в список групп
        List<List<Integer>> finalGroups = groupInfos.stream()
                .map(info -> info.group)
                .collect(Collectors.toList());

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        //вывод результатов в файл
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("output.txt"))) {
            writer.write(String.valueOf(finalGroups.size()));
//            writer.write( " групп с более чем одним элементом");        //в задании сказано вывести число, но можно добавить пояснение
            writer.newLine();
            writer.newLine();

            for (int i = 0; i < finalGroups.size(); i++) {
                List<Integer> group = finalGroups.get(i);
                GroupInfo info = groupInfos.get(i);

                //выводим информацию о группе с порядковым номером
                writer.write("Группа " + (i + 1));
                writer.newLine();

                for (int id : group) {
                    //восстанавливаем исходный формат строки
                    String[] tokens = tokenizedLines.get(id);
                    String line = Arrays.stream(tokens)
                            .map(token -> token.isEmpty() ? "\"\"" : "\"" + token + "\"")
                            .collect(Collectors.joining(";"));
                    writer.write(line);
                    writer.newLine();
                }
                writer.newLine();                 //добавляем пустую строку между группами
            }
        } catch (IOException e) {
            System.err.println("Ошибка записи в файл: " + e.getMessage());
            System.exit(4);
        }
        System.out.printf("Количество групп с более чем одним элементом: %s\n", finalGroups.size());
        System.out.printf("Время выполнения программы: %s мс\n", executionTime);
    }

    //валидация строки
private static boolean validateLine(String line) {
    int state = 0;                              //0: ожидаем начало токена (или пустую ячейку), 1: внутри кавычек, 2: после закрытой кавычки
    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        switch (state) {
            case 0:
                if (c == '"')
                    state = 1;                  //началось значение в кавычках
                else if (c == ';')
                    state = 0;                  //пустая ячейка
                else
                    return false;               //некорректный символ вне кавычек
                break;
            case 1:
                if (c == '"')
                    state = 2;                  //закрыли кавычку
                break;                          //иначе остаёмся внутри кавычек
            case 2:
                if (c == ';')
                    state = 0;                  //следующий токен
                else
                    return false;               //после закрытия кавычки должен быть `;` или конец строки
                break;
        }
    }
                                                //строка должна завершиться либо после закрытой кавычки, либо на пустом токене
    return state == 2 || state == 0;            //если исключим нулевое состояние, тогда избавимся от обработки пустых строк,
                                                //что даст значительную прибавку в производительности
}

    //класс для реализации алгоритма Disjoint Set Union (DSU) с оптимизациями сжатия путей и объединения по рангу.
    static class UnionFind {
        private int[] parent;
        private int[] rank;

        public UnionFind(int size) {
            parent = new int[size];
            rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;                     //изначально каждый элемент является сам себе родителем
                rank[i] = 0;                       //изначально ранг каждого дерева равен 0
            }
        }

        //операция Find с сжатием путей итеративный вариант (рекурсию сенил на while)
        public int find(int i) {
            int root = i;
            while (parent[root] != root) {
                root = parent[root];
            }
            // Сжатие путей
            while (i != root) {
                int next = parent[i];
                parent[i] = root;
                i = next;
            }
            return root;
        }

        //операция Union с объединением по рангу
        public void union(int i, int j) {
            int rootI = find(i);
            int rootJ = find(j);

            if (rootI != rootJ) {                    //если они уже не в одной группе - объединяем
                //объединение по рангу - присоединяем дерево с меньшим рангом к корню дерева с большим рангом
                if (rank[rootI] < rank[rootJ]) {
                    parent[rootI] = rootJ;
                } else if (rank[rootI] > rank[rootJ]) {
                    parent[rootJ] = rootI;
                } else {
                    //если ранги равны - присоединяем одно к другому и увеличиваем ранг нового корня
                    parent[rootJ] = rootI;
                    rank[rootI]++;
                }
            }
        }
    }
        //вспомогательный класс для хранения информации о группе
    static class GroupInfo {
        List<Integer> group;                            //список ID строк в группе
        int totalElements;                              //общее количество НЕПУСТЫХ элементов в группе
        int minId;                                      //минимальный ID в группе
        GroupInfo(List<Integer> group, int totalElements, int minId) {
            this.group = group;
            this.totalElements = totalElements;
            this.minId = minId;
        }
    }
}
