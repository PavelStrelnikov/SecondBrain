"""Утилиты для записей: разбор номера из имени файла и привязка к событию звонка."""
import re

_DIGITS = re.compile(r"\d{7,15}")


def phone_from_filename(name: str) -> str | None:
    """Samsung называет файлы по-разному: с именем контакта или с номером.
    Берём самую длинную цифровую последовательность, похожую на телефон."""
    candidates = _DIGITS.findall(name.replace("-", "").replace(" ", ""))
    if not candidates:
        return None
    return max(candidates, key=len)


def phones_match(a: str | None, b: str | None) -> bool:
    """Сравниваем по последним 7 цифрам, чтобы форматы +972 / 0 совпадали."""
    if not a or not b:
        return False
    da = re.sub(r"\D", "", a)[-7:]
    db = re.sub(r"\D", "", b)[-7:]
    return len(da) == 7 and da == db
