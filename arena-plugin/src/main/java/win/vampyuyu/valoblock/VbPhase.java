package win.vampyuyu.valoblock;

/** Match phases. QUEUE is the lobby before a game exists; a running match starts at AGENT_SELECT. */
public enum VbPhase {
  QUEUE,
  AGENT_SELECT,
  BUY,
  COMBAT,
  PLANTED,
  ROUND_END,
  END
}
