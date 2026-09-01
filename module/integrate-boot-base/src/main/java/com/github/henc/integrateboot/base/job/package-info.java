/**
 * The plain-Java job programming model: the {@link com.github.henc.integrateboot.base.job.Job}
 * annotation plus the {@link com.github.henc.integrateboot.base.job.JobContext} it may
 * receive. Business modules declare jobs against these types alone, so their code
 * compiles without the scheduling module; when that module is present and enabled, the
 * annotated methods are discovered and registered with the scheduling engine.
 */
package com.github.henc.integrateboot.base.job;
