import { useEffect, useRef } from "react";
import { defaultSkin } from "./ItemIcon";

// The player's own skin, drawn from the skin texture as the inventory screen shows it: head, body, arms and
// legs with their second layers, front view, one skin pixel = 8 canvas pixels.
const K = 8;

function load(src: string) {
  return new Promise<HTMLImageElement | null>((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

function draw(
  canvas: HTMLCanvasElement,
  img: HTMLImageElement,
  slim: boolean,
) {
  const g = canvas.getContext("2d")!;
  g.clearRect(0, 0, canvas.width, canvas.height);
  g.imageSmoothingEnabled = false;
  const modern = img.height >= 64;
  const arm = slim ? 3 : 4;
  // sx, sy, width, height on the skin; dx, dy on the figure (in skin pixels); mirror for old 64x32 skins.
  const part = (
    sx: number,
    sy: number,
    w: number,
    h: number,
    dx: number,
    dy: number,
    mirror = false,
  ) => {
    g.save();
    if (mirror) {
      g.translate((dx + w) * K, 0);
      g.scale(-1, 1);
      g.drawImage(img, sx, sy, w, h, 0, dy * K, w * K, h * K);
    } else g.drawImage(img, sx, sy, w, h, dx * K, dy * K, w * K, h * K);
    g.restore();
  };
  // Viewer's left is the character's right arm and leg.
  part(4, 20, 4, 12, 4, 20); // right leg
  part(modern ? 20 : 4, modern ? 52 : 20, 4, 12, 8, 20, !modern); // left leg
  part(20, 20, 8, 12, 4, 8); // body
  part(44, 20, arm, 12, 4 - arm, 8); // right arm
  part(modern ? 36 : 44, modern ? 52 : 20, arm, 12, 12, 8, !modern); // left arm
  part(8, 8, 8, 8, 4, 0); // head
  if (modern) {
    part(4, 36, 4, 12, 4, 20);
    part(4, 52, 4, 12, 8, 20);
    part(20, 36, 8, 12, 4, 8);
    part(44, 36, arm, 12, 4 - arm, 8);
    part(52, 52, arm, 12, 12, 8);
  }
  part(40, 8, 8, 8, 4, 0); // hat layer
}

export function PlayerSkin({ uuid, name }: { uuid: string; name: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    let live = true;
    let object = "";
    (async () => {
      let img: HTMLImageElement | null = null;
      let slim = false;
      try {
        const res = await fetch(`/api/v1/players/${encodeURIComponent(uuid)}/skin`);
        if (res.ok) {
          slim = res.headers.get("X-Skin-Model") === "slim";
          object = URL.createObjectURL(await res.blob());
          img = await load(object);
        }
      } catch {
        // No network to Mojang or an offline player: the default skin below stands in.
      }
      if (!img) ({ img, slim } = await defaultSkin(uuid));
      if (live && img && ref.current) draw(ref.current, img, slim);
    })();
    return () => {
      live = false;
      if (object) URL.revokeObjectURL(object);
    };
  }, [uuid]);
  return (
    <canvas
      ref={ref}
      className="mc-skin"
      width={16 * K}
      height={32 * K}
      role="img"
      aria-label={`${name}'s skin`}
    />
  );
}
