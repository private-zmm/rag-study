const chineseOrderedHeadingPattern = /^([一二三四五六七八九十]+|[0-9]+)[、．.]\s*(\S.{0,48})$/;
const chineseBracketHeadingPattern = /^[（(]([一二三四五六七八九十]+|[0-9]+)[）)]\s*(\S.{0,48})$/;
const markdownHeadingPattern = /^(#{1,6})\s*(.+)$/;
const tableLinePattern = /^\s*\|.+\|\s*$/;
const tableSeparatorPattern = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/;
const codeLanguages = [
  'bash',
  'sh',
  'shell',
  'sql',
  'java',
  'javascript',
  'typescript',
  'ts',
  'tsx',
  'json',
  'yaml',
  'yml',
  'xml',
  'html',
  'css',
  'python',
  'py',
  'go',
  'rust',
  'dockerfile',
  'ini',
  'conf',
].join('|');
const shellCommandStarts =
  '(sudo|psql|systemctl|find|cd|tar|scp|rm|mkdir|cat|ls|SHOW|SELECT|ALTER|CREATE|pg_dumpall|apt|docker|kubectl|/)';
const shellLanguagePattern = /^(bash|sh|shell)$/i;
const codeLanguageFencePattern = new RegExp('```(' + codeLanguages + ')(?=\\S)', 'gi');
const inlineLanguageCommandPattern = new RegExp('^`?(' + codeLanguages + ')' + shellCommandStarts + '([\\s\\S]*)`?$', 'i');
const embeddedLanguageCommandPattern = new RegExp('\\s+`?(' + codeLanguages + ')' + shellCommandStarts + '([^`]*)`?', 'gi');

export function normalizeMarkdownContent(content: string) {
  const expandedLines = expandMarkdownLines(repairMalformedCodeFences(content.replace(/\r\n/g, '\n')));
  const normalizedLines = expandedLines.map((line) => normalizeMarkdownLine(line));

  return ensureMarkdownSpacing(normalizedLines).join('\n');
}

function repairMalformedCodeFences(content: string) {
  return content
    .replace(codeLanguageFencePattern, '```$1\n')
    .replace(/([^\n`])```/g, '$1\n```');
}

function expandMarkdownLines(content: string) {
  const expandedLines: string[] = [];
  let inCodeBlock = false;
  let codeLanguage = '';

  content.split('\n').forEach((line) => {
    const fenceMatch = line.match(/^\s*(```|~~~)\s*([\w+-]*)?.*$/);

    if (fenceMatch) {
      expandedLines.push(normalizeFenceLine(fenceMatch[1], fenceMatch[2] ?? ''));

      if (inCodeBlock) {
        inCodeBlock = false;
        codeLanguage = '';
      } else {
        inCodeBlock = true;
        codeLanguage = fenceMatch[2] ?? '';
      }

      return;
    }

    if (inCodeBlock) {
      expandedLines.push(shellLanguagePattern.test(codeLanguage) ? normalizeShellCommandSpacing(line) : line);
      return;
    }

    expandedLines.push(...splitInlineMarkdownBlocks(line));
  });

  if (inCodeBlock) {
    expandedLines.push('```');
  }

  return expandedLines;
}

function normalizeFenceLine(marker: string, language: string) {
  const normalizedLanguage = language.trim().toLowerCase();
  return normalizedLanguage ? `${marker}${normalizedLanguage}` : marker;
}

function splitInlineMarkdownBlocks(line: string) {
  const protectedLine = line.trimEnd();

  if (!protectedLine) {
    return [line];
  }

  const inlineCommandMatch = protectedLine.trim().match(inlineLanguageCommandPattern);

  if (inlineCommandMatch) {
    return [
      `\`\`\`${inlineCommandMatch[1].toLowerCase()}`,
      normalizeShellCommandSpacing(`${inlineCommandMatch[2]}${inlineCommandMatch[3] ?? ''}`),
      '```',
    ];
  }

  return protectedLine
    .replace(/([^\n#])\s*(#{1,6})(?=\S)/g, '$1\n\n$2 ')
    .replace(/([。！？；:：])\s*(\|[^|\n]+?\|)/g, '$1\n\n$2')
    .replace(/(\|[^|\n]+?\|)\s+(\|[^|\n]+?\|)/g, '$1\n$2')
    .replace(embeddedLanguageCommandPattern, (_match, language: string, commandStart: string, rest: string) => {
      return `\n\n\`\`\`${language.toLowerCase()}\n${normalizeShellCommandSpacing(`${commandStart}${rest ?? ''}`)}\n\`\`\``;
    })
    .split('\n');
}

function normalizeMarkdownLine(line: string) {
  const trimmedLine = line.trim();

  if (!trimmedLine) {
    return '';
  }

  const headingMatch = trimmedLine.match(markdownHeadingPattern);

  if (headingMatch) {
    return `${headingMatch[1]} ${headingMatch[2].trim()}`;
  }

  const bracketHeadingMatch = trimmedLine.match(chineseBracketHeadingPattern);

  if (bracketHeadingMatch && looksLikeHeading(bracketHeadingMatch[2])) {
    return `### ${bracketHeadingMatch[2].trim()}`;
  }

  const orderedHeadingMatch = trimmedLine.match(chineseOrderedHeadingPattern);

  if (orderedHeadingMatch && looksLikeHeading(orderedHeadingMatch[2])) {
    return `## ${orderedHeadingMatch[2].trim()}`;
  }

  return line;
}

function ensureMarkdownSpacing(lines: string[]) {
  const result: string[] = [];
  let inCodeBlock = false;

  lines.forEach((line) => {
    const trimmedLine = line.trim();
    const isFence = /^\s*(```|~~~)/.test(line);

    if (isFence) {
      pushBlankBeforeBlock(result);
      result.push(line);
      inCodeBlock = !inCodeBlock;

      if (!inCodeBlock) {
        pushBlankAfterBlock(result);
      }

      return;
    }

    if (inCodeBlock) {
      result.push(line);
      return;
    }

    const isHeading = /^#{1,6}\s+\S/.test(trimmedLine);
    const isTableLine = tableLinePattern.test(trimmedLine);
    const isTableSeparatorLine = tableSeparatorPattern.test(trimmedLine);

    if (isHeading) {
      pushBlankBeforeBlock(result);
      result.push(trimmedLine);
      pushBlankAfterBlock(result);
      return;
    }

    if (isTableLine) {
      if (!tableLinePattern.test(result[result.length - 1] ?? '')) {
        pushBlankBeforeBlock(result);
      }

      if (isTableSeparatorLine && !tableLinePattern.test(previousNonBlankLine(result))) {
        result.push(buildFallbackTableHeader(trimmedLine));
      }

      result.push(trimmedLine);
      return;
    }

    result.push(line);
  });

  return trimRepeatedBlankLines(result);
}

function pushBlankBeforeBlock(lines: string[]) {
  if (lines.length > 0 && lines[lines.length - 1] !== '') {
    lines.push('');
  }
}

function pushBlankAfterBlock(lines: string[]) {
  if (lines[lines.length - 1] !== '') {
    lines.push('');
  }
}

function trimRepeatedBlankLines(lines: string[]) {
  const compactLines = lines.filter((line, index) => {
    return !(line === '' && lines[index - 1] === '');
  });

  while (compactLines[0] === '') {
    compactLines.shift();
  }

  while (compactLines[compactLines.length - 1] === '') {
    compactLines.pop();
  }

  return compactLines;
}

function previousNonBlankLine(lines: string[]) {
  for (let index = lines.length - 1; index >= 0; index -= 1) {
    if (lines[index].trim()) {
      return lines[index];
    }
  }

  return '';
}

function buildFallbackTableHeader(separatorLine: string) {
  const columnCount = parseTableCells(separatorLine).length || 1;
  return `| ${Array.from({ length: columnCount }, (_item, index) => `列 ${index + 1}`).join(' | ')} |`;
}

function parseTableCells(line: string) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim());
}

function normalizeShellCommandSpacing(command: string) {
  return command
    .trim()
    .replace(/^`+|`+$/g, '')
    .replace(/\b(sudo)(?=(systemctl|tar|chown|chmod|rm|find|psql|apt|cp|mv|mkdir|pg_dumpall))/g, 'sudo ')
    .replace(/\b(systemctl)(start|stop|restart|status)(postgresql)?/g, (_match, commandName, action, service) => {
      return `${commandName} ${action}${service ? ` ${service}` : ''}`;
    })
    .replace(/\b(apt)(install|update|upgrade|remove)/g, '$1 $2')
    .replace(/\b(pg_dumpall)(?=\||>|$|\s)/g, '$1')
    .replace(/\b(chown)(-R)?(?=\S)/g, (_match, commandName, recursive) => `${commandName}${recursive ? ' -R' : ''} `)
    .replace(/\b(chmod)(?=\d)/g, '$1 ')
    .replace(/\b(find)(?=\/|-)/g, '$1 ')
    .replace(/\b(cd|scp|rm|mkdir|cat|ls)(?=\/|-)/g, '$1 ')
    .replace(/\b(tar)(?=-)/g, '$1 ')
    .replace(/\b(psql)(?=-|\s|$)/g, '$1 ')
    .replace(/-U(?=\S)/g, '-U ')
    .replace(/-c(?=\S)/g, '-c ')
    .replace(/-name(?=\S)/g, '-name ')
    .replace(/-f(?=\/)/g, '-f ')
    .replace(/(postgresql|main|\.gz|\.signal)(sudo|systemctl|cd|tar|scp|rm|find|psql|chown|chmod)/g, '$1\n$2');
}

function looksLikeHeading(text: string) {
  const normalizedText = text.trim();

  if (!normalizedText || normalizedText.length > 48) {
    return false;
  }

  return !/[。！？；]$/.test(normalizedText);
}
