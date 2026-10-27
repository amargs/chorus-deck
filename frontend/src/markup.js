/** Lyric markup: **bold**, *italic*, __underline__. */

export function markupToHtml(markup) {
  return String(markup ?? '')
    .split('\n')
    .map((line) => `<div>${renderLine(line) || '<br>'}</div>`)
    .join('')
}

export function htmlToMarkup(root) {
  const parts = []
  walk(root, { bold: false, italic: false, underline: false }, parts, true)
  return parts.map((part) => (part.break ? '\n' : wrap(part.text, part.style))).join('').replace(/\u00a0/g, ' ')
}

function renderLine(line) {
  return parseLine(line).map((segment) => {
    let html = escapeHtml(segment.text)
    if (segment.bold) html = `<strong>${html}</strong>`
    if (segment.italic) html = `<em>${html}</em>`
    if (segment.underline) html = `<u>${html}</u>`
    return html
  }).join('')
}

function parseLine(line) {
  const segments = []
  let current = ''
  let bold = false
  let italic = false
  let underline = false
  const flush = () => {
    if (!current) return
    segments.push({ text: current, bold, italic, underline })
    current = ''
  }
  for (let i = 0; i < line.length;) {
    if (line.startsWith('**', i)) {
      flush()
      bold = !bold
      i += 2
    } else if (line.startsWith('__', i)) {
      flush()
      underline = !underline
      i += 2
    } else if (line[i] === '*') {
      flush()
      italic = !italic
      i += 1
    } else {
      current += line[i]
      i += 1
    }
  }
  flush()
  return segments
}

function walk(node, style, parts, isRoot) {
  if (node.nodeType === Node.TEXT_NODE) {
    pushText(parts, node.nodeValue, style)
    return
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return
  if (node.tagName === 'BR') {
    pushBreak(parts)
    return
  }
  const next = combine(style, node)
  const block = !isRoot && (node.tagName === 'DIV' || node.tagName === 'P')
  if (block && parts.length && !parts[parts.length - 1].break) {
    pushBreak(parts)
  }
  for (const child of node.childNodes) {
    walk(child, next, parts, false)
  }
}

function combine(parent, node) {
  const decoration = `${node.style?.textDecoration || ''} ${node.style?.textDecorationLine || ''}`
  const weight = node.style?.fontWeight
  return {
    bold: parent.bold || node.tagName === 'B' || node.tagName === 'STRONG' || weight === 'bold' || Number(weight) >= 600,
    italic: parent.italic || node.tagName === 'I' || node.tagName === 'EM' || node.style?.fontStyle === 'italic',
    underline: parent.underline || node.tagName === 'U' || decoration.includes('underline'),
  }
}

function pushText(parts, text, style) {
  if (!text) return
  const last = parts[parts.length - 1]
  if (last && !last.break && same(last.style, style)) {
    last.text += text
    return
  }
  parts.push({ text, style })
}

function pushBreak(parts) {
  if (parts[parts.length - 1]?.break) return
  parts.push({ break: true })
}

function same(left, right) {
  return left.bold === right.bold && left.italic === right.italic && left.underline === right.underline
}

function wrap(text, style) {
  let open = ''
  let close = ''
  if (style.underline) {
    open += '__'
    close = `__${close}`
  }
  if (style.italic) {
    open += '*'
    close = `*${close}`
  }
  if (style.bold) {
    open += '**'
    close = `**${close}`
  }
  return open + text + close
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
