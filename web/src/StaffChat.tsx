import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";

type Message = {
  id: number;
  at: string;
  from: "staff" | "player" | "system";
  text: string;
};
type Channel = {
  open: boolean;
  exists: boolean;
  online: boolean;
  messages: Message[];
  last: number;
};

/**
 * A private channel between the signed-in administrator and one player on one server. While it is open
 * the player's chat goes only here and these messages reach only that player. Nothing is stored by the
 * panel: closing the page keeps the channel open in the game until someone closes it or the player leaves.
 */
export function StaffChat({
  server,
  uuid,
  name,
  csrf,
  onError,
}: {
  server: string;
  uuid: string;
  name: string;
  csrf: string;
  onError: (error: unknown) => void;
}) {
  const [channel, setChannel] = useState<Channel | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const last = useRef(0);
  const failure = useRef(onError);
  failure.current = onError;
  const box = useRef<HTMLDivElement>(null);
  const alive = useRef(true);

  const call = useCallback(
    async (body: Record<string, unknown>) => {
      const next = await api<Channel>(`/servers/${server}/chat`, "POST", csrf, {
        uuid,
        since: last.current,
        ...body,
      });
      if (!alive.current) return next;
      if (next.messages.length) {
        last.current = next.last;
        setMessages((old) => [...old, ...next.messages].slice(-200));
      }
      setChannel(next);
      return next;
    },
    [server, uuid, csrf],
  );

  useEffect(() => {
    alive.current = true;
    last.current = 0;
    setMessages([]);
    setChannel(null);
    void call({ action: "read" }).catch(() => {
      /* the server may be stopped: the card then says the player is not online */
    });
    const timer = window.setInterval(() => {
      void call({ action: "read" }).catch(() => undefined);
    }, 2000);
    return () => {
      alive.current = false;
      window.clearInterval(timer);
    };
  }, [call]);

  useEffect(() => {
    const el = box.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  async function run(body: Record<string, unknown>) {
    setBusy(true);
    try {
      await call(body);
    } catch (e) {
      failure.current(e);
    } finally {
      setBusy(false);
    }
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    const message = text.trim();
    if (!message) return;
    setText("");
    await run({ action: "send", message });
  }

  const open = !!channel?.open;
  const online = !!channel?.online;
  return (
    <section className="staff-chat">
      <div className="staff-chat-head">
        <h3>Direct channel · {name}</h3>
        <span className={`status ${open ? "on" : ""}`}>
          <span />
          {open ? "Open" : channel?.exists ? "Closed" : "Not opened"}
        </span>
        {open ? (
          <button disabled={busy} onClick={() => void run({ action: "close" })}>
            Close channel
          </button>
        ) : (
          <button
            className="primary"
            disabled={busy || !online}
            title={online ? "" : `${name} is not online on this server`}
            onClick={() => void run({ action: "open" })}
          >
            Open channel
          </button>
        )}
      </div>
      <div
        className="staff-chat-log"
        ref={box}
        role="log"
        aria-label={`Private channel with ${name}`}
      >
        {messages.length ? (
          messages.map((m) => (
            <div key={m.id} className={`bubble ${m.from}`}>
              <span>{m.text}</span>
              <small>
                {new Date(m.at).toLocaleTimeString([], { hour12: false })}
              </small>
            </div>
          ))
        ) : (
          <p className="empty-log">
            {online
              ? `${name} is online here. Open the channel to talk to them privately.`
              : `${name} is not online on this server.`}
          </p>
        )}
      </div>
      <form className="staff-chat-form" onSubmit={send}>
        <input
          aria-label={`Message to ${name}`}
          placeholder={
            open ? `Message to ${name}…` : "Open the channel to write"
          }
          maxLength={256}
          value={text}
          disabled={!open || busy}
          onChange={(e) => setText(e.target.value)}
        />
        <button className="primary" disabled={!open || busy || !text.trim()}>
          Send
        </button>
      </form>
      <p className="console-note">
        While the channel is open, what {name} types goes only to staff: nobody
        else sees it. They are told when it opens and can leave with{" "}
        <code>/exitchat</code>. It closes when they disconnect.
      </p>
    </section>
  );
}
