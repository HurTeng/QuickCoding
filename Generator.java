import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.util.Pair;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;

public class Generator {
    private static final char CSV_LIST_SEPARATOR = '|';
    private static final String ENUM_CLASS_NAME = "ErrorEnum";
    private static final String FILE_SUFFIX_NAME = "java";
    private String inputFile;
    private String outputDir;
    private List<String> attributeList;

    public Generator(String inputFile, String outputDir) {
        this.inputFile = inputFile;
        this.outputDir = outputDir;
    }

    public void generate() {
        Map<String, Map<String, String>> dataMap = obtainDataMap();
        generateConstantFile(dataMap);
        generateEnumFile(dataMap);
    }

    private void generateConstantFile(Map<String, Map<String, String>> dataMap) {
        for (String attribute : attributeList) {
            String className = toBigHump(attribute);
            String filePath = obtainOutputFilePath(className);
            String content = obtainConstantTemplate(className, attribute, dataMap);
            generateFile(filePath, content);
        }
    }

    private void generateEnumFile(Map<String, Map<String, String>> dataMap) {
        String className = ENUM_CLASS_NAME;
        String filePath = obtainOutputFilePath(className);
        String content = obtainEnumTemplate(className, dataMap);
        generateFile(filePath, content);
    }

    private Map<String, Map<String, String>> obtainDataMap() {
        List<List<String>> dataList = new ArrayList<>();
        List<String> rowList = parseFile(inputFile);
        for (String row : rowList) {
            dataList.add(splitRowData(row));
        }
        attributeList = dataList.remove(0);

        Map<String, Map<String, String>> dataMap = new LinkedHashMap<>();
        int attributeSize = attributeList.size();
        for (List<String> rowDataList : dataList) {
            fillList(rowDataList, attributeSize);
            String name = obtainLabelName(rowDataList.get(0));
            Map<String, String> itemMap = new LinkedHashMap<>();
            dataMap.put(name, itemMap);

            for (int index = 0; index < attributeSize; index++) {
                String attribute = attributeList.get(index);
                String data = rowDataList.get(index);
                itemMap.put(attribute, data);
            }
        }

        return dataMap;
    }

    private List<String> splitRowData(String row) {
        List<String> list = new ArrayList<>();
        String regex = String.format("\\%s", CSV_LIST_SEPARATOR);
        String[] rowArr = row.split(regex);
        for (String data : rowArr) {
            list.add(data.trim());
        }
        return list;
    }

    private List<String> fillList(List<String> list, int size) {
        if (list.size() < size) {
            for (int index = 0; index < size - list.size(); index++) {
                list.add("");
            }
        }
        return list;
    }

    private String obtainLabelName(String name) {
        String regex = "\\s+";
        return name.trim().replaceAll(regex, "_").toUpperCase();
    }

    private String obtainPropertyName(String property) {
        return toSmallHump(property);
    }

    private String toBigHump(String str) {
        return toHump(str, false);
    }

    private String toSmallHump(String str) {
        return toHump(str, true);
    }

    private String toHump(String str, boolean isSmallHump) {
        StringBuilder builder = new StringBuilder();
        String[] strArr = str.split("[ _-]");
        String subStr = "";
        for (int index = 0; index < strArr.length; index++) {
            subStr = strArr[index];
            if (index != 0 || !isSmallHump) {
                subStr = capitalize(subStr);
            }
            builder.append(subStr);
        }
        return builder.toString();
    }

    public String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String obtainConstantTemplate(String className, String attribute,
            Map<String, Map<String, String>> dataMap) {
        StringBuilder builder = new StringBuilder();
        builder.append("public interface ").append(className).append(" {\n");
        for (Map.Entry<String, Map<String, String>> data : dataMap.entrySet()) {
            String format = String.format("\tString %s = \"%s\";\n", data.getKey(), data.getValue().get(attribute));
            builder.append(format);
        }
        builder.append("}\r\n");
        return builder.toString();
    }

    private String obtainEnumTemplate(String className, Map<String, Map<String, String>> dataMap) {
        String format = "public enum %s {\n" + "%s\n" + "%s\n" + "%s\n" + "%s" + "}";
        return String.format(format, className, obtainEnumDataList(dataMap), obtainEnumConstructor(className),
                obtainEnumProperty(), obtainEnumPropertyGetter());
    }

    private String obtainEnumDataList(Map<String, Map<String, String>> dataMap) {
        StringBuilder builder = new StringBuilder();
        List<String> propertyList = obtainEnumPropertyList();
        ArrayList<String> nameList = new ArrayList<>(dataMap.keySet());
        for (int index = 0; index < nameList.size(); index++) {
            String separator = index < nameList.size() - 1 ? "," : ";";
            String name = nameList.get(index);
            Map<String, String> itemMap = dataMap.get(name);
            String propertyArrString = propertyList.stream()
                    .map(property -> String.format("\"%s\"", itemMap.get(property)))
                    .collect(Collectors.joining(", ", "(", ")"));

            builder.append("\t").append(name).append(propertyArrString).append(separator).append("\n");
        }
        return builder.toString();
    }

    private String obtainEnumConstructor(String className) {
        StringBuilder builder = new StringBuilder();
        List<String> propertyList = obtainEnumPropertyList();
        String propertyArrString = propertyList.stream()
                .map(property -> String.format("String %s", obtainPropertyName(property)))
                .collect(Collectors.joining(", ", "(", ")"));

        builder.append("\t").append(className).append(propertyArrString).append(" {\n");

        for (String property : propertyList) {
            builder.append("\t\tthis.").append(property).append(" = ").append(property).append(";\n");
        }

        builder.append("\t}\n");
        return builder.toString();
    }

    private String obtainEnumProperty() {
        StringBuilder builder = new StringBuilder();
        List<String> propertyList = obtainEnumPropertyList();
        for (String property : propertyList) {
            builder.append("\tprivate String ").append(obtainPropertyName(property)).append(";\n");
        }
        return builder.toString();
    }

    private String obtainEnumPropertyGetter() {
        StringBuilder builder = new StringBuilder();
        List<String> propertyList = obtainEnumPropertyList();
        for (String property : propertyList) {
            builder.append("\tpublic String get").append(toBigHump(property)).append("() {\n");
            builder.append("\t\treturn ").append(obtainPropertyName(property)).append(";\n").append("\t}\n\n");
        }
        return builder.toString();
    }

    private List<String> obtainEnumPropertyList() {
        return attributeList;
    }

    private String obtainOutputFilePath(String name) {
        preGenerateDir(outputDir);
        return String.format("%s%s%s.%s", outputDir, File.separator, name, FILE_SUFFIX_NAME);
    }

    private void preGenerateDir(String directory) {
        File file = new File(directory);
        if (!file.exists() && !file.isDirectory()) {
            file.mkdirs();
        }
    }

    private File preGenerateFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }

    private void generateFile(String filePath, String content) {
        PrintWriter writer = null;
        try {
            File file = preGenerateFile(filePath);
            writer = new PrintWriter(new FileOutputStream(file));
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private List<String> parseFile(String filePath) {
        List<String> list = new ArrayList<>();
        BufferedReader reader = null;
        try {
            String line;
            reader = new BufferedReader(new FileReader(filePath));
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public static void main(String[] args) {
        String currentPath = new File("").getAbsolutePath();
        String inputFile = currentPath + File.separator + "error-code.csv";
        String outputDir = currentPath + File.separator + "output" + File.separator + "java";
        new Generator(inputFile, outputDir).generate();
    }
}