import { formatDate, isDate, isDayjsObject, isFunction } from '@ydsz-core/shared/utils';

import type { YDSZFormProps } from './types';

/**
 * 表单值转换器
 * @description 负责表单值的格式转换，包括范围时间值处理、数组与字符串互转等
 */
export class FormValueTransformer {
  private getState: () => null | YDSZFormProps;

  constructor(getState: () => null | YDSZFormProps) {
    this.getState = getState;
  }

  /**
   * 处理范围时间值，将范围字段拆分为开始和结束时间
   * @param originValues 原始表单值
   * @returns 处理后的表单值
   */
  handleRangeTimeValue(originValues: Record<string, any>) {
    const values = { ...originValues };
    const fieldMappingTime = this.getState()?.fieldMappingTime;

    this.handleStringToArrayFields(values);

    if (!fieldMappingTime || !Array.isArray(fieldMappingTime)) {
      return values;
    }

    fieldMappingTime.forEach(
      ([field, [startTimeKey, endTimeKey], format = 'YYYY-MM-DD']) => {
        if (startTimeKey && endTimeKey && values[field] === null) {
          Reflect.deleteProperty(values, startTimeKey);
          Reflect.deleteProperty(values, endTimeKey);
        }

        if (!values[field]) {
          Reflect.deleteProperty(values, field);
          return;
        }

        const [startTime, endTime] = values[field];
        if (format === null) {
          values[startTimeKey] = startTime;
          values[endTimeKey] = endTime;
        } else if (isFunction(format)) {
          values[startTimeKey] = format(startTime, startTimeKey);
          values[endTimeKey] = format(endTime, endTimeKey);
        } else {
          const [startTimeFormat, endTimeFormat] = Array.isArray(format)
            ? format
            : [format, format];

          values[startTimeKey] = startTime
            ? formatDate(startTime, startTimeFormat)
            : undefined;
          values[endTimeKey] = endTime
            ? formatDate(endTime, endTimeFormat)
            : undefined;
        }
        Reflect.deleteProperty(values, field);
      },
    );
    return values;
  }

  /**
   * 将数组字段转换为字符串
   * @param originValues 原始表单值
   */
  handleArrayToStringFields(originValues: Record<string, any>) {
    const arrayToStringFields = this.getState()?.arrayToStringFields;
    if (!arrayToStringFields || !Array.isArray(arrayToStringFields)) {
      return;
    }

    const processFields = (fields: string[], separator: string = ',') => {
      this.processFields(fields, separator, originValues, (value, sep) =>
        Array.isArray(value) ? value.join(sep) : value,
      );
    };

    if (arrayToStringFields.every((item) => typeof item === 'string')) {
      const lastItem =
        arrayToStringFields[arrayToStringFields.length - 1] || '';
      const fields =
        lastItem.length === 1
          ? arrayToStringFields.slice(0, -1)
          : arrayToStringFields;
      const separator = lastItem.length === 1 ? lastItem : ',';
      processFields(fields, separator);
      return;
    }

    arrayToStringFields.forEach((fieldConfig) => {
      if (Array.isArray(fieldConfig)) {
        const [fields, separator = ','] = fieldConfig;
        if (!Array.isArray(fields)) {
          console.warn(
            `Invalid field configuration: fields should be an array of strings, got ${typeof fields}`,
          );
          return;
        }
        processFields(fields, separator);
      }
    });
  }

  /**
   * 将字符串字段转换为数组
   * @param originValues 原始表单值
   */
  handleStringToArrayFields(originValues: Record<string, any>) {
    const arrayToStringFields = this.getState()?.arrayToStringFields;
    if (!arrayToStringFields || !Array.isArray(arrayToStringFields)) {
      return;
    }

    const processFields = (fields: string[], separator: string = ',') => {
      this.processFields(fields, separator, originValues, (value, sep) => {
        if (typeof value !== 'string') {
          return value;
        }
        if (value === '') {
          return [];
        }
        const escapedSeparator = sep.replaceAll(
          /[.*+?^${}()|[\]\\]/g,
          String.raw`\$&`,
        );
        return value.split(new RegExp(escapedSeparator));
      });
    };

    if (arrayToStringFields.every((item) => typeof item === 'string')) {
      const lastItem =
        arrayToStringFields[arrayToStringFields.length - 1] || '';
      const fields =
        lastItem.length === 1
          ? arrayToStringFields.slice(0, -1)
          : arrayToStringFields;
      const separator = lastItem.length === 1 ? lastItem : ',';
      processFields(fields, separator);
      return;
    }

    arrayToStringFields.forEach((fieldConfig) => {
      if (Array.isArray(fieldConfig)) {
        const [fields, separator = ','] = fieldConfig;
        if (Array.isArray(fields)) {
          processFields(fields, separator);
        } else if (typeof originValues[fields] === 'string') {
          const value = originValues[fields];
          if (value === '') {
            originValues[fields] = [];
          } else {
            const escapedSeparator = separator.replaceAll(
              /[.*+?^${}()|[\]\\]/g,
              String.raw`\$&`,
            );
            originValues[fields] = value.split(new RegExp(escapedSeparator));
          }
        }
      }
    });
  }

  /**
   * 通用字段处理方法
   * @param fields 字段名列表
   * @param separator 分隔符
   * @param originValues 原始表单值
   * @param transformFn 转换函数
   */
  private processFields(
    fields: string[],
    separator: string,
    originValues: Record<string, any>,
    transformFn: (value: any, separator: string) => any,
  ) {
    fields.forEach((field) => {
      const value = originValues[field];
      if (value === undefined || value === null) {
        return;
      }
      originValues[field] = transformFn(value, separator);
    });
  }
}
