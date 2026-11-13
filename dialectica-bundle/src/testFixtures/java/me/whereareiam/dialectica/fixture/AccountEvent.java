package me.whereareiam.dialectica.fixture;

import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.Nullable;
import me.whereareiam.dialectica.entity.annotation.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An event of an account, with a point in time and a date, as a project would keep them.
 */
@Table("identica_account_events")
public record AccountEvent(
		@Id @Length(64) String eventKey,
		UUID uniqueId,
		Instant happenedAt,
		@Nullable LocalDate dueOn
) {
}
