package com.j111.docqa.entity;

/**
 * 文档处理状态。上传后立刻是 PENDING，线程池里解析完变 SUCCESS / FAILED。
 */
public enum DocumentStatus {

    /** 已落盘，等待解析 */
    PENDING,

    /** 正在解析 */
    PARSING,

    /** 解析成功，纯文本已产出 */
    SUCCESS,

    /** 解析失败，原因见 errorMessage */
    FAILED
}
