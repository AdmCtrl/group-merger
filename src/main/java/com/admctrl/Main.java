package com.admctrl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        //В задании ничего не сказано про обработку ошибок при не корректном использовании
        //Также можно бросить RuntimeException для аварийного завершения через необработанное исключение
/*        if (args.length != 1) {         //если запущено без аргументов, или несколькими
            System.exit(1);               //общая ошибка
        }*/

        Path path = Paths.get(args[0]);
/*        if (!Files.exists(path)) {      //если не найден файл
            System.exit(2);               //аварийное завершение с пользовательским кодом. Файл не найден
        }*/

        List<String> validLines = null;                          //объявляем список результатов вне блока, для обеспеченя области видимости
        try (Stream<String> lines = Files.lines(path)) {         //пытаемся считать в стрим файл построчно, файл содержит перенос строк, это точно известно
            validLines = lines                                   //создаем список. используем цикл, константная память O(1), при регулярке O(n)
                    .filter(line -> {                            //фильтруем некорректные строки, находим хоть одно не соответствие - прерываем анализ строки
                        int state = 0;                           //0: ожидаем открывающую кавычку, 1: внутри кавычек, 2: после закрытой кавычки
                        for (int i = 0; i < line.length(); i++) {
                            char c = line.charAt(i);
                            switch (state) {
                                case 0:                           //должна быть открывающая кавычка
                                    if (c != '"') return false;
                                    state = 1;
                                    break;
                                case 1:                           //внутри кавычек
                                    if (c == '"') state = 2;
                                    break;
                                case 2:                           //после закрытой кавычки
                                    if (c == ';')
                                        state = 0;                //после ; должна быть новая кавычка
                                    else return false;            //любой символ кроме ; недопустим
                                    break;
                            }
                        }
                        return state == 2;                        //должны закончить в состоянии после кавычки
                    })
                    .collect(Collectors.toCollection(() -> new ArrayList<>(1_000_000))); //можно выяснить точное значени capacity через lines.count(), но это затратно

        } catch (
                IOException e) {                                  //обрабатываем исключение, если файл не может быть прочитан или не найден
            System.err.println("Ошибка чтения файла: " + e.getMessage());
            System.exit(3);                               //аварийное завершение с пользовательским кодом. Не возможно прочитать файл, или его нет, если мы не обработали эту ситуацию
        }
        System.out.println("Всего корректных строк в файле: " + validLines.size());
    }
}
