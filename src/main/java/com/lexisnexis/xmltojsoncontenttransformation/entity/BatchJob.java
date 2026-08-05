package com.lexisnexis.xmltojsoncontenttransformation.entity;

import com.lexisnexis.xmltojsoncontenttransformation.constant.BatchStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@RequiredArgsConstructor
public class BatchJob {

    private final String batchId;
    private final String inputDir;
    private final Instant submittedAt = Instant.now();
    private final AtomicInteger published = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    @Setter
    private volatile int totalFiles;

    @Setter
    private volatile BatchStatus status = BatchStatus.RUNNING;

    @Setter
    private volatile Instant completedAt;
}
