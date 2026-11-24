/** Same limit as the API (TagService.MAX_LENGTH). */
export const TAG_MAX_LENGTH = 40;

/** Same cleaning as the API: spaces trimmed and collapsed, a leading "#" removed. */
export const cleanTagName = (value: string) => value.trim().replace(/\s+/g, ' ').replace(/^#\s*/, '');
