"""公共模型单元测试。"""

from datetime import date, datetime

import pytest
from pydantic import ValidationError

from common.models import (
    Activity,
    AgentResponse,
    DailyJournal,
    DailySummaryResult,
    DialogState,
    DialogSummary,
    ImageAnnotation,
    MatchDetail,
    MatchResult,
    MatchResponse,
    Mood,
    Platform,
    SocialCopy,
    SocialCopyResponse,
    TimelineEntry,
)


class TestMoodEnum:
    def test_all_moods_have_values(self):
        for mood in Mood:
            assert mood.value

    def test_mood_from_string(self):
        assert Mood("开心") == Mood.HAPPY
        assert Mood("平静") == Mood.CALM


class TestActivityEnum:
    def test_all_activities_have_values(self):
        for act in Activity:
            assert act.value

    def test_other_is_last_resort(self):
        assert Activity("其他") == Activity.OTHER


class TestTimelineEntry:
    def test_minimal_entry(self):
        entry = TimelineEntry(
            entry_id="1",
            user_id="u1",
            timestamp=datetime(2026, 7, 23, 14, 30),
            entry_type="note",
            content_ref="Hello",
        )
        assert entry.entry_id == "1"
        assert entry.location_name is None

    def test_full_entry(self):
        entry = TimelineEntry(
            entry_id="2",
            user_id="u1",
            timestamp=datetime(2026, 7, 23, 15, 0),
            entry_type="photo",
            content_ref="oss://bucket/photo1.jpg",
            thumbnail_url="https://cdn.example.com/thumb.jpg",
            location_name="望京SOHO",
            location_lat=39.995,
            location_lng=116.470,
        )
        assert entry.location_name == "望京SOHO"
        assert entry.thumbnail_url is not None


class TestDailyJournal:
    def test_empty_journal(self):
        journal = DailyJournal(
            journal_id="j1",
            user_id="u1",
            date=date(2026, 7, 23),
        )
        assert len(journal.entries) == 0
        assert journal.summary is None

    def test_journal_with_entries(self):
        journal = DailyJournal(
            journal_id="j1",
            user_id="u1",
            date=date(2026, 7, 23),
            entries=[
                TimelineEntry(
                    entry_id="e1", user_id="u1",
                    timestamp=datetime(2026, 7, 23, 10, 0),
                    entry_type="photo", content_ref="pic1",
                ),
            ],
            mood=Mood.HAPPY,
            tags=["咖啡", "桂花"],
        )
        assert len(journal.entries) == 1
        assert journal.mood == Mood.HAPPY
        assert "咖啡" in journal.tags


class TestDialogSummary:
    def test_default_values(self):
        summary = DialogSummary()
        assert summary.location == ""
        assert len(summary.people) == 0

    def test_full_summary(self):
        summary = DialogSummary(
            location="望京",
            activity="咖啡",
            sensory="桂花香",
            mood="平静",
            people=["小明"],
            highlights="闻到了第一缕桂花香",
            raw_summary="今天去了望京的咖啡店，闻到了桂花香",
        )
        assert summary.location == "望京"
        assert "小明" in summary.people


class TestAgentResponse:
    def test_simple_reply(self):
        resp = AgentResponse(text="听起来不错", is_final=False)
        assert resp.text == "听起来不错"
        assert resp.is_final is False
        assert resp.summary is None

    def test_final_response_with_summary(self):
        summary = DialogSummary(location="望京", mood="平静")
        resp = AgentResponse(text="已保存。", is_final=True, summary=summary)
        assert resp.is_final is True
        assert resp.summary.location == "望京"


class TestDailySummaryResult:
    def test_minimal_result(self):
        result = DailySummaryResult(
            user_id="u1",
            date=date(2026, 7, 23),
        )
        assert result.keywords == []
        assert result.summary == ""

    def test_full_result(self):
        result = DailySummaryResult(
            user_id="u1",
            date=date(2026, 7, 23),
            keywords=["咖啡", "桂花", "平静"],
            summary="今天你在望京的咖啡店度过了平静的午后。",
            mood="平静",
            highlight="在咖啡店闻到了桂花香",
        )
        assert len(result.keywords) == 3


class TestSocialCopy:
    def test_copy_creation(self):
        copy = SocialCopy(
            copy_id="c1",
            platform=Platform.WECHAT_MOMENTS,
            text="今天也是充实的一天～",
            hashtags=[],
        )
        assert copy.platform == Platform.WECHAT_MOMENTS

    def test_full_copy(self):
        copy = SocialCopy(
            copy_id="c2",
            platform=Platform.XIAOHONGSHU,
            text="分享一家超棒的咖啡店！",
            hashtags=["#咖啡探店", "#望京"],
            recommended_image_index=0,
        )
        assert len(copy.hashtags) == 2


class TestMatchResult:
    def test_match_result(self):
        detail = MatchDetail(type="location", value="望京")
        result = MatchResult(
            matched_user_id="u2",
            similarity=0.85,
            common_details=[detail],
            ice_break="你也去了望京？",
        )
        assert result.similarity == pytest.approx(0.85)
        assert len(result.common_details) == 1

    def test_city_vibe_match(self):
        result = MatchResult(
            matched_user_id=None,
            type="city_vibe",
            similarity=0.0,
            ice_break="今天有 3 个人也在望京附近",
        )
        assert result.type == "city_vibe"
        assert result.matched_user_id is None


class TestStreamMessage:
    def test_stream_message_creation(self):
        from common.models import StreamMessage
        msg = StreamMessage(
            stream="ai.summary.request",
            message_id="12345-0",
            data={"user_id": "u1", "date": "2026-07-23"},
        )
        assert msg.stream == "ai.summary.request"
        assert msg.data["user_id"] == "u1"
