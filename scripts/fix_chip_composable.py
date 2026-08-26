#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

old = '''                    fun chip(label: String, onClick: () -> Unit) {
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable(onClick = onClick),
                        ) {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    // local chips as Surfaces (Compose local fun not allowed in lambda — inline)
'''

if old not in t:
    if "fun chip(" not in t:
        print("already fixed")
    else:
        raise SystemExit("pattern not found")
else:
    t = t.replace(old, "", 1)
    p.write_text(t)
    print("removed fun chip", p.stat().st_size)

assert "fun chip(" not in t
print("ok")
