# coding=UTF-8

import sys
import os
import codecs
import re

FILE_CODING = 'utf-8' 
CSV_LIST_SEPARATOR = '|'
ENUM_FILE_NAME = "error_enum";
FILE_SUFFIX_NAME = "py";

class Generator:
    def __init__(self, input_file=None, output_dir=None):
        if output_dir is None:
            output_dir = os.path.split(os.path.realpath(__file__))[0]
        self.input_file = input_file
        self.output_dir = output_dir
        self.attribute_list = []
        self.templates = []

    def generate(self):
        data_map = self.obtain_data_map()
        self.generate_constant_file(data_map)
        self.generate_enum_file(data_map)

    def generate_constant_file(self, data_map):
        for attribute in self.attribute_list:
            name = self.to_underline(attribute)
            output_file = self.obtain_output_file_path(name)
            content = self.obtain_constant_template(name, attribute, data_map)
            self.generate_file(content, output_file)

    def generate_enum_file(self, data_map):
        name = ENUM_FILE_NAME
        output_file = self.obtain_output_file_path(name)
        content = self.obtain_enum_template(name, data_map)
        self.generate_file(content, output_file)

    def obtain_data_map(self):
        data_list = []
        row_list = self.parse_file()
        for row in row_list:
            data_list.append(self.split_row_data(row))

        self.attribute_list = data_list.pop(0)
        data_map = {}
        attribute_size = len(self.attribute_list)
        for row_data_list in data_list:
            row_data_list = self.fill_list(row_data_list, attribute_size)
            name = self.obtain_label_name(row_data_list[0])
            data_map[name] = {}
            for index in range(attribute_size):
                attribute = self.attribute_list[index]
                data_map[name][attribute] = row_data_list[index]
        return data_map

    def split_row_data(self, row): 
        list = []
        row_arr = row.strip().split(CSV_LIST_SEPARATOR)
        for data in row_arr:
            list.append(data.strip())
        return list

    def fill_list(self, list, size):
        if len(list) < size:
            for index in range(size - len(list)):
                list.append("")
        return list

    def obtain_label_name(self, name):
        return re.sub('\\s+', '_', name.strip().upper())

    def to_underline(self, str):
        return str

    def obtain_constant_template(self, name, attribute, data_map):
        template = 'class {name}:\n'.format(name=name)
        for key, value in data_map.items():
            template += '\t{key} = \'{value}\'\n'.format(key=key, value=value[attribute])
        template += '\r\n'
        return template

    def obtain_enum_template(self, name, data_map):
        return '{import_package}\n' \
            '{enum_property}\n' \
            '@unique\nclass {name}(Enum):\n' \
            '{data_list}\n' \
            '{property_getter}' \
                .format(import_package=self.obtain_enum_import_package(), enum_property=self.obtain_enum_property(), name=name, data_list=self.obtain_enum_data_list(data_map), property_getter=self.obtain_enum_property_getter())

    def obtain_enum_data_list(self, data_map):
        template = ''
        property_list = self.obtain_enum_property_list()
        name_list = list(data_map.keys())
        namedtuple = self.obtain_enum_namedtuple()
        for index in range(len(name_list)):
            name = name_list[index]
            property_arr = ", ".join('\'{}\''.format(data_map[name][property]) for property in property_list)
            template += '\t{name} = {namedtuple}({property_arr})\n' \
                .format(name=name, namedtuple=namedtuple, property_arr=property_arr)
        return template

    def obtain_enum_property(self):
        namedtuple = self.obtain_enum_namedtuple()
        property_list = self.obtain_enum_property_list()
        property_arr = ', '.join('\'{}\''.format(property) for property in property_list)
        return '{namedtuple} = namedtuple(\'data\', [{property_arr}])\n'.format(namedtuple=namedtuple, property_arr=property_arr)

    def obtain_enum_namedtuple(self):
        return 'Tuple'

    def obtain_enum_import_package(self):
        return 'from enum import Enum, unique\n' \
            'from collections import namedtuple\n'

    def obtain_enum_property_getter(self):
        template = ''
        property_list = self.obtain_enum_property_list()
        for property in property_list:
            template += '\t@property\n' \
            '\tdef {property}(self):\n' \
            '\t\treturn self.value.{property}\n\n'.format(property=property)
        return template

    def obtain_enum_property_list(self):
        return self.attribute_list
        
    def obtain_output_file_path(self, name):
        self.pre_generate_dir(self.output_dir)
        return '{directory}{separator}{name}.{suffix}' \
            .format(directory=self.output_dir, separator=os.sep, name=name, suffix=FILE_SUFFIX_NAME)

    def pre_generate_dir(self, directory):
        if not os.path.exists(directory):
            os.mkdir(directory)

    def generate_file(self, content, file):
        try:
            writer = open(file, 'w')
            writer.write(content)
            writer.close()
        except Exception as e:
            print(e)

    def parse_file(self):
        try:
            reader = codecs.open(self.input_file, 'r', FILE_CODING)
            data_list = []
            for line in reader:
                data_list.append(line)
            reader.close()
        except FileNotFoundError:
            print('file not found')
        except Exception as e:
            print(e)
        return data_list

if __name__ == '__main__':
    current_path = os.path.dirname(sys.argv[0])
    input_file = os.path.join(current_path,'error-code.csv')
    output_dir = os.path.join(current_path, 'output', 'python')
    Generator(input_file, output_dir).generate()
