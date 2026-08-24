import { sql } from "kysely";
import { executeSqliteQueryTakeFirstSync } from "../../infra/kysely-sync.js";
import {
  getActiveTranscriptKysely,
  withCurrentProjectionSnapshot,
} from "./session-accessor.sqlite-active-events.js";
import type { SessionTranscriptReadScope } from "./session-accessor.sqlite-contract.js";
import { resolveVisibleMessagePositions } from "./session-accessor.sqlite-reset-window.js";

/** Reads active-path visible message count and JSONL byte size without materializing payloads. */
export function readSessionTranscriptActiveStats(scope: SessionTranscriptReadScope): {
  eventCount: number;
  sizeBytes: number;
} {
  return withCurrentProjectionSnapshot(scope, (projection) => {
    const visible = resolveVisibleMessagePositions(projection);
    if (visible.total === 0) {
      return { eventCount: 0, sizeBytes: 0 };
    }
    const db = getActiveTranscriptKysely(projection.database);
    const row = executeSqliteQueryTakeFirstSync(
      projection.database.db,
      db
        .selectFrom("session_transcript_active_events as active")
        .innerJoin("transcript_events as event", (join) =>
          join
            .onRef("event.session_id", "=", "active.session_id")
            .onRef("event.seq", "=", "active.event_seq"),
        )
        .select((eb) => [
          eb.fn.count<number>("active.event_seq").as("event_count"),
          /* kysely-allow-raw: JSONL size includes one terminating newline per event. */
          sql<number>`COALESCE(SUM(LENGTH(CAST(event.event_json AS BLOB))), 0)
            + COUNT(*)`.as("size_bytes"),
        ])
        .where("active.session_id", "=", projection.resolved.sessionId)
        .where("active.message_position", "is not", null)
        .where((eb) =>
          visible.kept.length > 0
            ? eb.or([
                eb("active.message_position", ">=", eb.val(visible.postStart)),
                eb("active.message_position", "in", visible.kept),
              ])
            : eb("active.message_position", ">=", eb.val(visible.postStart)),
        ),
    );
    return {
      eventCount: row?.event_count ?? 0,
      sizeBytes: row?.size_bytes ?? 0,
    };
  });
}
