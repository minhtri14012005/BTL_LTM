export function latestRoom(previous, incoming) {
  return previous?.id === incoming.id && previous.revision > incoming.revision ? previous : incoming;
}
