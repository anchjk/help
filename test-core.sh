#!/usr/bin/env bash
# Kiểm thử logic thuần Java (không cần Android SDK): nghiệp vụ ca/phiên/cảnh báo + luồng nhập lịch ca từ ảnh.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build/core-test"
SRC="$ROOT/app/src/main/java/vn/evnqt1/helpdesk"
TST="$ROOT/app/src/test/java"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$SRC/core/ShiftOccurrence.java" \
  "$SRC/core/ShiftResolver.java" \
  "$SRC/core/SessionRules.java" \
  "$SRC/core/TextNormalizer.java" \
  "$SRC/core/AlertRuleEngine.java" \
  "$SRC/core/ScheduleDraft.java" \
  "$SRC/core/ScheduleRules.java" \
  "$SRC/core/XlsxReader.java" \
  "$SRC/core/CsvReader.java" \
  "$SRC/core/ScheduleSheetParser.java" \
  "$SRC/core/AnalysisResult.java" \
  "$SRC/core/AnalysisRange.java" \
  "$SRC/core/AssistantIntent.java" \
  "$SRC/core/MatchSuggester.java" \
  "$SRC/core/AnomalyCategorizer.java" \
  "$SRC/core/StatsBuckets.java" \
  "$SRC/core/StatsReport.java" \
  "$SRC/core/StatsCalculator.java" \
  "$TST/vn/evnqt1/helpdesk/core/CoreSmokeTest.java" \
  "$TST/vn/evnqt1/helpdesk/core/ScheduleSmokeTest.java" \
  "$TST/vn/evnqt1/helpdesk/core/AssistantSmokeTest.java" \
  "$TST/vn/evnqt1/helpdesk/core/MatchSuggesterSmokeTest.java" \
  "$TST/vn/evnqt1/helpdesk/core/StatsSmokeTest.java"
java -cp "$OUT" vn.evnqt1.helpdesk.core.CoreSmokeTest
(cd "$ROOT" && java -cp "$OUT" vn.evnqt1.helpdesk.core.ScheduleSmokeTest docs/mau/Lich_Truc_Ca_Thang_11_2026.xlsx)
java -cp "$OUT" vn.evnqt1.helpdesk.core.AssistantSmokeTest
java -cp "$OUT" vn.evnqt1.helpdesk.core.MatchSuggesterSmokeTest
java -cp "$OUT" vn.evnqt1.helpdesk.core.StatsSmokeTest
