const path = require('path');
const fs = require('fs');
const readLine = require('readline');
const FILE_SUFFIX_NAME = 'js'
const CSV_LIST_SEPARATOR = '|';

class Generator {

    constructor(inputFile, outputDir) {
        this.inputFile = inputFile;
        this.outputDir = outputDir;
    }

    generate() {
        this.obtainMapData().then(dataMap => {
            this.generateConstantFile(dataMap);
            this.generateEnumFile(dataMap);
        });
    }

    generateConstantFile(dataMap) {
        for (let attribute of this.attributeList) {
            let className = attribute;
            let filePath = this.obtainOutputFilePath(className);
            let content = this.obtainConstantTemplate(attribute, dataMap);
            this.generateFile(filePath, content);
        }
    }

    generateEnumFile(dataMap) {
        let className = 'error_enum';
        let filePath = this.obtainOutputFilePath(className);
        let content = this.obtainEnumTemplate(dataMap);
        this.generateFile(filePath, content);
    }

    obtainEnumTemplate(dataMap) {
        let builder = [];
        builder.push("module.exports = Object.freeze({\n");
        builder.push(this.obtainEnumDataList(dataMap));
        builder.push("});\n");
        return builder.join("");
    }

    obtainEnumDataList(dataMap) {
        let builder = [];
        let propertyList = this.obtainEnumPropertyList();
        let nameList = Object.keys(dataMap);
        for (let index = 0; index < nameList.length; index++) {
            let separator = index < nameList.length - 1 ? "," : "";
            let name = nameList[index];
            let propertyArrString = propertyList.map(property => `${property}: "${dataMap[name][property]}"`).join(", ");
            builder.push(`\t${name}: {${propertyArrString}}${separator}\n`);
        }
        return builder.join("");
    }

    obtainEnumPropertyList() {
        let list = [];
        for (let index = 1; index < this.attributeList.length; index++) {
            let tag = this.attributeList[index];
            list.push(tag);
        }
        return list;
    }

    obtainConstantTemplate(attribute, map) {
        let builder = [];
        builder.push("module.exports = Object.freeze({\n");
        for (const [key, value] of Object.entries(map)) {
            let format = `\t${key}: \"${value[attribute]}\",\n`;
            builder.push(format);
        }
        builder.push("})\r\n");
        return builder.join("");
    }

    async obtainMapData() {
        let rowList = await this.parseFile(this.inputFile);
        let dataList = rowList.map(row => this.splitRowData(row));
        this.attributeList = dataList.shift();

        let dataMap = {};
        const attributeSize = this.attributeList.length;
        for (let rowDataList of dataList) {
            this.fillList(rowDataList, attributeSize);
            let name = this.obtainKeyName(rowDataList[0]);
            let itemMap = {};
            dataMap[name] = itemMap;

            for (let index = 0; index < attributeSize; index++) {
                let attribute = this.attributeList[index];
                itemMap[attribute] = rowDataList[index];
            }
        }

        return dataMap
    }

    splitRowData(row) {
        let rowList = row.split(CSV_LIST_SEPARATOR);
        return rowList.map(data => data.trim());
    }

    fillList(list, size) {
        if (list.length < size) {
            for (let index = 0; index < size - list.length; index++) {
                list.push("");
            }
        }
        return list;
    }

    obtainKeyName(name) {
        let regex = new RegExp("\\s+", "g");
        return name.trim().replace(regex, "_").toUpperCase();
    }

    obtainOutputFilePath(name) {
        this.preGenerateDir(outputDir);
        return `${this.outputDir}${path.sep}${name}.${FILE_SUFFIX_NAME}`;
    }

    preGenerateDir(directory) {
        fs.mkdir(directory, () => {});
    }

    generateFile(filePath, content) {
        fs.writeFile(filePath, content, function (err) {
            if (err) {
                console.error(err);
            }
        })
    }

    parseFile(filePath) {
        return new Promise(function (resolve, reject) {
            try {
                let list = [];
                readLine.createInterface({
                        input: fs.createReadStream(filePath)
                    })
                    .on('line', (line) => list.push(line))
                    .on('close', () => resolve(list));
            } catch (err) {
                reject(err);
            }
        })
    }
}

const currentPath = path.resolve('./');
const inputFile = currentPath + path.sep + 'error-code.csv';
const outputDir = currentPath + path.sep + 'output' + path.sep + 'javascript';
new Generator(inputFile, outputDir).generate();