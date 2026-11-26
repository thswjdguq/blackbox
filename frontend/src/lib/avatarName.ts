// 얼굴(동그라미)에 넣는 짧은 이름. 한글 이름은 성을 빼고 이름 앞 두 글자(김민지 → 민지, 남궁민수 → 민수),
// 두 글자 이름은 이름 한 글자(이호 → 호). 한글이 아닌 이름은 앞 두 글자를 대문자로(예전과 같음).
// 두 글자 성은 네 글자 이상일 때만 본다. 세 글자면 한 글자 성이 훨씬 흔하다(황보람 → 보람).
const TWO_LETTER_SURNAMES = ["남궁", "제갈", "선우", "황보", "독고", "사공", "서문", "동방"];

export function avatarName(name: string | null | undefined, max = 2): string {
  const chars = Array.from((name ?? "").replace(/\s+/g, ""));
  if (!chars.length) return "";
  if (!chars.every((c) => /[가-힣]/.test(c))) return chars.slice(0, max).join("").toUpperCase();
  if (chars.length === 1) return chars[0];
  const surname = chars.length >= 4 && TWO_LETTER_SURNAMES.includes(chars.slice(0, 2).join("")) ? 2 : 1;
  return chars.slice(surname, surname + max).join("");
}
