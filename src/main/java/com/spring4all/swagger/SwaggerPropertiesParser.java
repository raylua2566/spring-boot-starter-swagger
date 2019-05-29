package com.spring4all.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.*;
import com.google.common.collect.Maps;
import com.spring4all.swagger.model.DocketInfo;
import com.spring4all.swagger.model.GlobalOperationParameter;
import com.spring4all.swagger.model.GlobalResponseMessage;
import com.spring4all.swagger.model.GlobalResponseMessageBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.builders.*;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.ApiKeyVehicle;

import java.util.*;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;

public class SwaggerPropertiesParser {

    private static Logger logger = LoggerFactory.getLogger(SwaggerPropertiesParser.class);

    private final static ObjectMapper om = new ObjectMapper();

    private final SwaggerProperties swaggerProperties;
    private final Map<String, DocketInfo> docketMapper;

    public SwaggerPropertiesParser(SwaggerProperties swaggerProperties) {

        this.swaggerProperties = Preconditions.checkNotNull(swaggerProperties,
                "ERROR: SwaggerPropertiesParser对象不能为空..."
        );

        docketMapper = initDocketInfoList(this.swaggerProperties);
    }

    public List<Docket> getDocketList() {
        return this
                .docketMapper
                .entrySet()
                .stream()
                .map(this::buildDocket)
                .collect(Collectors.toList());
    }

    private Docket buildDocket(Map.Entry<String, DocketInfo> entry) {
        DocketInfo docketInfo = entry.getValue();
        Docket docketForBuilder = new Docket(DocumentationType.SWAGGER_2)
                .host(docketInfo.getHost())
                .apiInfo(
                        buildApiInfo(docketInfo)
                )
                .securityContexts(
                        Collections.singletonList(
                                securityContext(
                                        swaggerProperties.getAuthorization().getName(),
                                        swaggerProperties.getAuthorization().getAuthRegex()
                                )
                        )
                )
                .globalOperationParameters(
                        assemblyGlobalOperationParameters(
                                swaggerProperties.getGlobalOperationParameters(),
                                docketInfo.getGlobalOperationParameters()
                        )
                );

        // base-path处理
        // 当没有配置任何path的时候，解析/**
        if (docketInfo.getBasePath().isEmpty()) {
            docketInfo.getBasePath().add("/**");
        }

        List<Predicate<String>> basePath = new ArrayList();
        for (String path : docketInfo.getBasePath()) {
            basePath.add(PathSelectors.ant(path));
        }

        // exclude-path处理
        List<Predicate<String>> excludePath = new ArrayList();
        for (String path : docketInfo.getExcludePath()) {
            excludePath.add(PathSelectors.ant(path));
        }

        if ("BasicAuth".equalsIgnoreCase(swaggerProperties.getAuthorization().getType())) {
            docketForBuilder.securitySchemes(Collections.singletonList(basicAuth()));
        } else if (!"None".equalsIgnoreCase(swaggerProperties.getAuthorization().getType())) {
            docketForBuilder.securitySchemes(Collections.singletonList(apiKey()));
        }

        // 全局响应消息
        if (!swaggerProperties.getApplyDefaultResponseMessages()) {
            buildGlobalResponseMessage(swaggerProperties, docketForBuilder);
        }

        Docket docket = docketForBuilder.groupName(entry.getKey())
                .select()
                .apis(RequestHandlerSelectors.basePackage(docketInfo.getBasePackage()))
                .paths(
                        Predicates.and(
                                Predicates.not(Predicates.or(excludePath)),
                                Predicates.or(basePath)
                        )
                )
                .build();

        /* ignoredParameterTypes **/
        Class<?>[] array = new Class[docketInfo.getIgnoredParameterTypes().size()];
        Class<?>[] ignoredParameterTypes = docketInfo.getIgnoredParameterTypes().toArray(array);
        docket.ignoredParameterTypes(ignoredParameterTypes);

        return docket;

    }

    private ApiInfo buildApiInfo(DocketInfo docketInfo) {
        return new ApiInfoBuilder()
                .title(docketInfo.getTitle())
                .description(docketInfo.getDescription())
                .version(docketInfo.getVersion())
                .license(docketInfo.getLicense())
                .licenseUrl(docketInfo.getLicenseUrl())
                .contact(
                        new springfox.documentation.service.Contact(
                                docketInfo.getContact().getName(),
                                docketInfo.getContact().getUrl(),
                                docketInfo.getContact().getEmail()
                        )
                )
                .termsOfServiceUrl(docketInfo.getTermsOfServiceUrl())
                .build();
    }

    private Map<String, DocketInfo> initDocketInfoList(SwaggerProperties swaggerProperties) {
        Map<String, DocketInfo> docketMapper = Maps.newLinkedHashMap();
        if (swaggerProperties.getDocket().size() == 0) {
            docketMapper.put("default", swaggerProperties);
        } else {
            docketMapper.putAll(swaggerProperties.getDocket());
            docketMapper.values().forEach(x->initDocketInfo(swaggerProperties, x));
        }



        if (logger.isDebugEnabled()) {
            StringBuilder debugInfo = new StringBuilder(System.lineSeparator() +
                    "---------------- 初始化Swagger DockerInfos({})-----------------");
            for(Map.Entry entry : docketMapper.entrySet()) {
                debugInfo.append(System.lineSeparator());
                debugInfo.append(
                        String.format(
                                "%s:" + System.lineSeparator() + "%s",
                                entry.getKey(),
                                prettifyModel(entry.getValue(), 4)
                        )
                );
            }
            debugInfo.append(System.lineSeparator());
            logger.debug(debugInfo.toString(), docketMapper.size());
        }

        return docketMapper;
    }

    private String prettifyModel(Object value, int initIndent) {
        String indentString = Strings.repeat(" ", initIndent);

        ObjectMapper om = new ObjectMapper();
        try {
            String result = om.writer().with(new DefaultPrettyPrinter()).writeValueAsString(value);
            Splitter splitter = Splitter.onPattern(System.lineSeparator());
            ArrayList<String> lines = newArrayList(splitter.split(result));
            return lines.stream().map(x-> indentString + x + System.lineSeparator()).collect(Collectors.joining());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Json格式化对象["+value.getClass().getName()+"]失败",e);
        }
    }

    private void initDocketInfo(DocketInfo defaultInfo, DocketInfo initInfo) {
        /*
title
description
version
license
licenseUrl
termsOfServiceUrl
host
contact
basePackage
        */
        initInfo.setTitle(initInfo.getTitle().isEmpty() ? defaultInfo.getTitle() : initInfo.getTitle());
        initInfo.setDescription(initInfo.getDescription().isEmpty() ? defaultInfo.getDescription() : initInfo.getDescription());
        initInfo.setVersion(initInfo.getVersion().isEmpty() ? defaultInfo.getVersion() : initInfo.getVersion());
        initInfo.setLicense(initInfo.getLicense().isEmpty() ? defaultInfo.getLicense() : initInfo.getLicense());
        initInfo.setLicenseUrl(initInfo.getLicenseUrl().isEmpty() ? defaultInfo.getLicenseUrl() : initInfo.getLicenseUrl());
        initInfo.setTermsOfServiceUrl(initInfo.getTermsOfServiceUrl().isEmpty() ? defaultInfo.getTermsOfServiceUrl() : initInfo.getTermsOfServiceUrl());
        initInfo.setHost(initInfo.getHost().isEmpty() ? defaultInfo.getHost() : initInfo.getHost());
        initInfo.setBasePackage(initInfo.getBasePackage().isEmpty() ? defaultInfo.getBasePackage() : initInfo.getBasePackage());


        com.spring4all.swagger.model.Contact contact = initInfo.getContact();
        if (contact != null) {
            contact.setUrl(Optional.ofNullable(contact.getUrl()).orElse(defaultInfo.getContact().getUrl()));
            contact.setEmail(Optional.ofNullable(contact.getEmail()).orElse(defaultInfo.getContact().getEmail()));
            contact.setName(Optional.ofNullable(contact.getName()).orElse(defaultInfo.getContact().getName()));
        } else {
            initInfo.setContact(defaultInfo.getContact());
        }

    }


    /**
     * 配置基于 ApiKey 的鉴权对象
     *
     * @return
     */
    private ApiKey apiKey() {
        return new ApiKey(swaggerProperties.getAuthorization().getName(),
                swaggerProperties.getAuthorization().getKeyName(),
                ApiKeyVehicle.HEADER.getValue());
    }

    /**
     * 配置基于 BasicAuth 的鉴权对象
     *
     * @return
     */
    private BasicAuth basicAuth() {
        return new BasicAuth(swaggerProperties.getAuthorization().getName());
    }

    /**
     * 配置默认的全局鉴权策略的开关，以及通过正则表达式进行匹配；默认 ^.*$ 匹配所有URL
     * 其中 securityReferences 为配置启用的鉴权策略
     *
     * @return
     */
    private static SecurityContext securityContext(String reference, String authPathRegx) {
        return SecurityContext.builder()
                .securityReferences(defaultAuth(reference))
                .forPaths(PathSelectors.regex(authPathRegx))
                .build();
    }

    /**
     * 配置默认的全局鉴权策略；其中返回的 SecurityReference 中，reference 即为ApiKey对象里面的name，保持一致才能开启全局鉴权
     *
     * @return
     */
    private static List<SecurityReference> defaultAuth(String reference) {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Collections.singletonList(SecurityReference.builder()
                .reference(reference)
                .scopes(authorizationScopes).build());
    }

    private static List<Parameter> buildGlobalOperationParametersFromSwaggerProperties(
            List<GlobalOperationParameter> globalOperationParameters) {
        List<Parameter> parameters = newArrayList();

        if (Objects.isNull(globalOperationParameters)) {
            return parameters;
        }
        for (GlobalOperationParameter globalOperationParameter : globalOperationParameters) {
            parameters.add(new ParameterBuilder()
                    .name(globalOperationParameter.getName())
                    .description(globalOperationParameter.getDescription())
                    .modelRef(new ModelRef(globalOperationParameter.getModelRef()))
                    .parameterType(globalOperationParameter.getParameterType())
                    .required(Boolean.parseBoolean(globalOperationParameter.getRequired()))
                    .build());
        }
        return parameters;
    }

    /**
     * 设置全局响应消息
     *
     * @param swaggerProperties swaggerProperties 支持 POST,GET,PUT,PATCH,DELETE,HEAD,OPTIONS,TRACE
     * @param docketForBuilder  swagger docket builder
     */
    private static void buildGlobalResponseMessage(SwaggerProperties swaggerProperties, Docket docketForBuilder) {

        GlobalResponseMessage globalResponseMessages =
                swaggerProperties.getGlobalResponseMessage();

        /* POST,GET,PUT,PATCH,DELETE,HEAD,OPTIONS,TRACE 响应消息体 **/
        List<ResponseMessage> postResponseMessages = getResponseMessageList(globalResponseMessages.getPost());
        List<ResponseMessage> getResponseMessages = getResponseMessageList(globalResponseMessages.getGet());
        List<ResponseMessage> putResponseMessages = getResponseMessageList(globalResponseMessages.getPut());
        List<ResponseMessage> patchResponseMessages = getResponseMessageList(globalResponseMessages.getPatch());
        List<ResponseMessage> deleteResponseMessages = getResponseMessageList(globalResponseMessages.getDelete());
        List<ResponseMessage> headResponseMessages = getResponseMessageList(globalResponseMessages.getHead());
        List<ResponseMessage> optionsResponseMessages = getResponseMessageList(globalResponseMessages.getOptions());
        List<ResponseMessage> trackResponseMessages = getResponseMessageList(globalResponseMessages.getTrace());

        docketForBuilder.useDefaultResponseMessages(swaggerProperties.getApplyDefaultResponseMessages())
                .globalResponseMessage(RequestMethod.POST, postResponseMessages)
                .globalResponseMessage(RequestMethod.GET, getResponseMessages)
                .globalResponseMessage(RequestMethod.PUT, putResponseMessages)
                .globalResponseMessage(RequestMethod.PATCH, patchResponseMessages)
                .globalResponseMessage(RequestMethod.DELETE, deleteResponseMessages)
                .globalResponseMessage(RequestMethod.HEAD, headResponseMessages)
                .globalResponseMessage(RequestMethod.OPTIONS, optionsResponseMessages)
                .globalResponseMessage(RequestMethod.TRACE, trackResponseMessages);
    }


    /**
     * 获取返回消息体列表
     *
     * @param globalResponseMessageBodyList 全局Code消息返回集合
     * @return
     */
    private static List<ResponseMessage> getResponseMessageList
    (List<GlobalResponseMessageBody> globalResponseMessageBodyList) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        for (GlobalResponseMessageBody globalResponseMessageBody : globalResponseMessageBodyList) {
            ResponseMessageBuilder responseMessageBuilder = new ResponseMessageBuilder();
            responseMessageBuilder.code(globalResponseMessageBody.getCode()).message(globalResponseMessageBody.getMessage());

            if (!StringUtils.isEmpty(globalResponseMessageBody.getModelRef())) {
                responseMessageBuilder.responseModel(new ModelRef(globalResponseMessageBody.getModelRef()));
            }
            responseMessages.add(responseMessageBuilder.build());
        }

        return responseMessages;
    }

    /**
     * 局部参数按照name覆盖局部参数
     *
     * @param globalOperationParameters
     * @param docketOperationParameters
     * @return
     */
    private static List<Parameter> assemblyGlobalOperationParameters(
            List<GlobalOperationParameter> globalOperationParameters,
            List<GlobalOperationParameter> docketOperationParameters) {

        if (Objects.isNull(docketOperationParameters) || docketOperationParameters.isEmpty()) {
            return buildGlobalOperationParametersFromSwaggerProperties(globalOperationParameters);
        }

        Set<String> docketNames = docketOperationParameters.stream()
                .map(GlobalOperationParameter::getName)
                .collect(Collectors.toSet());

        List<GlobalOperationParameter> resultOperationParameters = newArrayList();

        if (Objects.nonNull(globalOperationParameters)) {
            for (GlobalOperationParameter parameter : globalOperationParameters) {
                if (!docketNames.contains(parameter.getName())) {
                    resultOperationParameters.add(parameter);
                }
            }
        }

        resultOperationParameters.addAll(docketOperationParameters);
        return buildGlobalOperationParametersFromSwaggerProperties(resultOperationParameters);
    }
}
