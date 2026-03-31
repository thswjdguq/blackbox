"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";

/**
 * PageProgress — 페이지 전환 시 상단에 얇은 진행 바 표시
 * pathname이 바뀔 때 200ms 애니메이션 후 사라짐
 */
export default function PageProgress() {
  const pathname = usePathname();
  const [visible, setVisible] = useState(false);
  const [width,   setWidth]   = useState(0);

  useEffect(() => {
    // 페이지 바뀔 때마다 진행 바 재생
    setWidth(0);
    setVisible(true);

    const t1 = setTimeout(() => setWidth(80), 50);    // 빠르게 80%
    const t2 = setTimeout(() => setWidth(100), 300);  // 완료
    const t3 = setTimeout(() => setVisible(false), 550);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, [pathname]);

  if (!visible) return null;

  return (
    <div
      style={{ width: `${width}%`, transition: "width 0.25s ease" }}
      className="fixed top-0 left-0 h-0.5 bg-bb-primary z-[9999]"
    />
  );
}
