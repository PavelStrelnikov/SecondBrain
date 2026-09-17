from app.recordings_util import phone_from_filename, phones_match


def test_phone_from_filename_with_number():
    assert phone_from_filename("Call recording 0525929152_260917_143012.m4a") == "0525929152"


def test_phone_from_filename_with_name_only():
    assert phone_from_filename("Call recording David_260917.m4a") is None


def test_phones_match_across_formats():
    assert phones_match("+972525929152", "0525929152")
    assert not phones_match("0525929152", "0524687828")
    assert not phones_match(None, "0525929152")
