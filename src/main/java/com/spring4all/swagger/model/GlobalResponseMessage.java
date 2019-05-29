package com.spring4all.swagger.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class GlobalResponseMessage {

    /**
     * POST 响应消息体
     **/
    List<GlobalResponseMessageBody> post = new ArrayList<>();

    /**
     * GET 响应消息体
     **/
    List<GlobalResponseMessageBody> get = new ArrayList<>();

    /**
     * PUT 响应消息体
     **/
    List<GlobalResponseMessageBody> put = new ArrayList<>();

    /**
     * PATCH 响应消息体
     **/
    List<GlobalResponseMessageBody> patch = new ArrayList<>();

    /**
     * DELETE 响应消息体
     **/
    List<GlobalResponseMessageBody> delete = new ArrayList<>();

    /**
     * HEAD 响应消息体
     **/
    List<GlobalResponseMessageBody> head = new ArrayList<>();

    /**
     * OPTIONS 响应消息体
     **/
    List<GlobalResponseMessageBody> options = new ArrayList<>();

    /**
     * TRACE 响应消息体
     **/
    List<GlobalResponseMessageBody> trace = new ArrayList<>();

}
