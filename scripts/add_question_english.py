#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Add English (text_en / optionA.text_en, optionB.text_en / optionA_en, optionB_en) to test question JSON files."""

import json
import os

DATA_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'static', 'data')


def add_bigfive():
    path = os.path.join(DATA_DIR, 'bigfive_questions.json')
    en_path = os.path.join(DATA_DIR, 'bigfive_questions_en.txt')
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    with open(en_path, 'r', encoding='utf-8') as f:
        lines = [line.strip() for line in f if line.strip()]
    if len(lines) != len(data):
        raise ValueError('bigfive_questions_en.txt must have {} lines, got {}'.format(len(data), len(lines)))
    for i, item in enumerate(data):
        item['text_en'] = lines[i]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print('bigfive_questions.json: added text_en for', len(data), 'items')


def add_enneagram():
    path = os.path.join(DATA_DIR, 'enneagram_questions.json')
    en_path = os.path.join(DATA_DIR, 'enneagram_questions_en.txt')
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    with open(en_path, 'r', encoding='utf-8') as f:
        lines = [line.strip() for line in f if line.strip()]
    # Format: for each question, two lines: optionA text_en, optionB text_en (use first 2*N lines only)
    expected = len(data) * 2
    if len(lines) < expected:
        raise ValueError('enneagram_questions_en.txt must have at least {} lines (2 per question), got {}'.format(expected, len(lines)))
    lines = lines[:expected]
    for i, item in enumerate(data):
        item['optionA']['text_en'] = lines[i * 2]
        item['optionB']['text_en'] = lines[i * 2 + 1]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print('enneagram_questions.json: added text_en for', len(data), 'items')


def add_mbti():
    path = os.path.join(DATA_DIR, 'mbti_questions.json')
    en_path = os.path.join(DATA_DIR, 'mbti_questions_en.txt')
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    with open(en_path, 'r', encoding='utf-8') as f:
        lines = [line.strip() for line in f if line.strip()]
    # Format: for each question, three lines: text_en, optionA_en, optionB_en
    expected = len(data) * 3
    if len(lines) != expected:
        raise ValueError('mbti_questions_en.txt must have {} lines (3 per question), got {}'.format(expected, len(lines)))
    for i, item in enumerate(data):
        item['text_en'] = lines[i * 3]
        item['optionA_en'] = lines[i * 3 + 1]
        item['optionB_en'] = lines[i * 3 + 2]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print('mbti_questions.json: added text_en, optionA_en, optionB_en for', len(data), 'items')


def add_mmpi():
    path = os.path.join(DATA_DIR, 'mmpi_questions.json')
    en_path = os.path.join(DATA_DIR, 'mmpi_questions_en.txt')
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    with open(en_path, 'r', encoding='utf-8') as f:
        lines = [line.strip() for line in f if line.strip()]
    if len(lines) != len(data):
        raise ValueError('mmpi_questions_en.txt must have {} lines, got {}'.format(len(data), len(lines)))
    for i, item in enumerate(data):
        item['text_en'] = lines[i]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print('mmpi_questions.json: added text_en for', len(data), 'items')


if __name__ == '__main__':
    import sys
    add_bigfive()
    add_enneagram()
    add_mbti()
    if os.path.exists(os.path.join(DATA_DIR, 'mmpi_questions_en.txt')):
        add_mmpi()
    else:
        print('mmpi_questions_en.txt not found; skipping MMPI. Add 566 lines of English to enable.')
    print('Done.')
