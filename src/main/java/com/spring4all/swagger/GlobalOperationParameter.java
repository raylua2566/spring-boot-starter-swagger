package com.spring4all.swagger;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GlobalOperationParameter {
    /**
     * 参数名
     **/
    private String name;

    /**
     * 描述信息
     **/
    private String description;

    /**
     * 指定参数类型
     **/
    private String modelRef;

    /**
     * 参数放在哪个地方:header,query,path,body.form
     **/
    private String parameterType;

    /**
     * 参数是否必须传
     **/
    private String required;

}
