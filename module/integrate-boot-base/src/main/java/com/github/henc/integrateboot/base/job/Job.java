package com.github.henc.integrateboot.base.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a schedulable job — the annotation-only way to declare tasks on
 * plain Spring beans.
 *
 * <p>The annotation lives in this base module (plain Java, no framework dependency) on
 * purpose: business modules may declare jobs while depending on base alone, so code that
 * carries {@code @Job} methods still compiles when the scheduling module is absent — the
 * annotation is then simply inert. Once the scheduling module is on the classpath and
 * scheduling is enabled ({@code integrate-boot.scheduling.enabled=true}), every
 * discovered {@code @Job} method is registered with the scheduling engine under its task
 * id.
 *
 * <p>Supported method signatures (the return value, if any, is ignored):
 * <ul>
 * <li>{@code void handle()} — no arguments
 * <li>{@code void handle(JobContext context)} — receives task parameters and sharding info
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class ReportJobs {
 *
 *     @Job("monthlyReport")
 *     public void monthlyReport(JobContext context) {
 *         String jobParam = context.parameters().get("jobParam");
 *         // ...
 *     }
 *
 *     @Job  // task id defaults to the method name: "cleanupTempFiles"
 *     public void cleanupTempFiles() {
 *         // ...
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Job {

    /**
     * The task id under which the job is registered with the scheduling engine (for
     * XXL-JOB based scheduling this is the JobHandler name configured on the admin
     * side). Must be unique across all jobs of the application. When left blank the
     * method name is used.
     */
    String value() default "";
}
