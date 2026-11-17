package org.chenile.jgen.blueprint.process.config;

import java.util.Map;

/**
 * One generated schedule, as written in a blueprint's schedule specification and emitted into the
 * generated project's schedules.json. It mirrors the fields of chenile-scheduler's
 * SchedulerDefinition; the generator keeps its own copy so that it does not depend on the Chenile
 * version it generates for.
 */
public class ScheduleSpec {
  public String name;
  public String tenant;
  public boolean enabled = true;
  public String cronExpression;
  public String timezone = "UTC";
  public String eventId = "ProcessCreate";
  public String description;
  public Map<String, Object> payload;
}
