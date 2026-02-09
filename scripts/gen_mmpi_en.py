#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate mmpi_questions_en.txt: 566 English lines for MMPI items (same order as JSON)."""

import json
import os

DATA_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'static', 'data')
JSON_PATH = os.path.join(DATA_DIR, 'mmpi_questions.json')
OUT_PATH = os.path.join(DATA_DIR, 'mmpi_questions_en.txt')

def main():
    with open(JSON_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)
    n = len(data)
    # Build English list: use provided EN_ITEMS for first 59, then translate from Chinese for rest
    en = []
    for i, item in enumerate(data):
        text = item.get('text', '')
        # First 59 use standard MMPI-style English
        if i == 0: en.append("I like to read mechanics magazines")
        elif i == 1: en.append("I have a good appetite")
        elif i == 2: en.append("When I wake up in the morning I usually feel well rested and clear-headed")
        elif i == 3: en.append("I think I would like the work of a librarian")
        elif i == 4: en.append("I am easily awakened by noise")
        elif i == 5: en.append("I like to read crime news in the newspaper")
        elif i == 6: en.append("My hands and feet are usually warm")
        elif i == 7: en.append("My daily life is full of things that interest me")
        elif i == 8: en.append("My ability to work (or study) is about the same as before")
        elif i == 9: en.append("I often feel as if there is something stuck in my throat")
        elif i == 10: en.append("A person should try to understand their dreams and get guidance from them")
        elif i == 11: en.append("I like detective or mystery novels")
        elif i == 12: en.append("I always work under a great deal of tension")
        elif i == 13: en.append("I have diarrhea at least once or twice a month")
        elif i == 14: en.append("I occasionally think of things too bad to say out loud")
        elif i == 15: en.append("I am convinced that life has been cruel to me")
        elif i == 16: en.append("My father was a good man")
        elif i == 17: en.append("I rarely have trouble with constipation")
        elif i == 18: en.append("When I start a new job I like to be told who I should get to know")
        elif i == 19: en.append("My sex life is satisfactory")
        elif i == 20: en.append("Sometimes I have a very strong desire to leave home")
        elif i == 21: en.append("Sometimes I laugh or cry in a way I cannot control")
        elif i == 22: en.append("Nausea and vomiting trouble me")
        elif i == 23: en.append("It seems no one understands me")
        elif i == 24: en.append("I would like to be a singer")
        elif i == 25: en.append("When I am in trouble I think it is best to keep my mouth shut")
        elif i == 26: en.append("Sometimes I feel as if there are spirits inside me")
        elif i == 27: en.append("When someone wrongs me I feel I should get even when I get the chance")
        elif i == 28: en.append("I am bothered by excess stomach acid several times a week")
        elif i == 29: en.append("Sometimes I feel like swearing")
        elif i == 30: en.append("I have nightmares every few nights")
        elif i == 31: en.append("I find it hard to keep my mind on a task or job")
        elif i == 32: en.append("I have had very unusual and strange experiences")
        elif i == 33: en.append("I cough often")
        elif i == 34: en.append("If people had not had it in for me I would have achieved much more")
        elif i == 35: en.append("I rarely worry about my health")
        elif i == 36: en.append("I have never gotten into trouble because of my sexual behavior")
        elif i == 37: en.append("When I was young I stole things occasionally for a while")
        elif i == 38: en.append("Sometimes I feel like smashing things")
        elif i == 39: en.append("Often I would rather sit and daydream than do anything")
        elif i == 40: en.append("I have had days or weeks or months when I could not take interest in anything because I had no energy")
        elif i == 41: en.append("My family is not satisfied with the job I have chosen (or will choose)")
        elif i == 42: en.append("My sleep is restless and I am easily awakened")
        elif i == 43: en.append("I feel pain in various parts of my head")
        elif i == 44: en.append("Sometimes I tell lies")
        elif i == 45: en.append("My judgment is better than it ever was")
        elif i == 46: en.append("At least once or twice a week I suddenly feel hot all over for no reason")
        elif i == 47: en.append("When I am with people it bothers me to hear strange or unusual stories")
        elif i == 48: en.append("It would be better if all laws were done away with")
        elif i == 49: en.append("Sometimes I feel my soul has left my body")
        elif i == 50: en.append("My body is as healthy as most of my friends")
        elif i == 51: en.append("When I meet classmates or acquaintances I pretend not to see them unless they speak first")
        elif i == 52: en.append("A minister or priest can cure disease by prayer and laying hands on the sick")
        elif i == 53: en.append("Most people who know me like me")
        elif i == 54: en.append("I have never been troubled by pain in my chest or heart")
        elif i == 55: en.append("When I was young I was sometimes punished for mischief at school")
        elif i == 56: en.append("I get along with people easily as soon as I meet them")
        elif i == 57: en.append("Everything is prearranged by fate or heaven")
        elif i == 58: en.append("I often take orders from people who are not as capable as I am")
        else:
            # Simple English placeholder from common MMPI-style translations (60-565)
            en.append(translate_item(i, text))
    with open(OUT_PATH, 'w', encoding='utf-8') as f:
        f.write('\n'.join(en))
    print('Wrote', len(en), 'lines to', OUT_PATH)

def translate_item(idx, zh):
    """Return English for item idx (0-based). Fallback: generic English."""
    # Map remaining items 60-565 to English (abbreviated list; extend as needed)
    t = {
        59: "I do not read every editorial in the paper every day",
        60: "I have never had a normal life",
        61: "Parts of my body often have feelings like burning, tingling, crawling, or numbness",
        62: "My bowel movements are regular and not hard to control",
        63: "Sometimes I keep on doing something until others lose patience",
        64: "I love my father",
        65: "I can see things, animals, or people around me that others cannot see",
        66: "I wish I could be as happy as others seem to be",
        67: "I have never felt pain at the back of my neck",
        68: "People of my own sex are strongly attracted to me",
        69: "I used to enjoy playing drop-the-handkerchief",
        70: "I think many people exaggerate their misfortunes to get sympathy and help",
        71: "I am bothered by an upset stomach every few days or more often",
        72: "I am an important person",
        73: "Male: I have often wished I were a girl. Female: I have never been sorry I am a woman",
        74: "I get angry sometimes",
        75: "I often feel downcast and hopeless",
        76: "I like to read love stories",
        77: "I like poetry",
        78: "My feelings are not easily hurt",
        79: "I sometimes tease animals",
        80: "I think I would like the work of a forest ranger",
        81: "When I argue I often lose to others",
        82: "Anyone who is able and willing to work hard can succeed",
        83: "Lately I find it easy to give up hope about some things",
        84: "Sometimes I am strongly attracted by others' things (e.g. shoes, gloves) and want to touch or take them even though I have no use for them",
        85: "I really lack self-confidence",
        86: "I would like to be a florist",
        87: "I always feel that life is worthwhile",
        88: "It takes a lot of argument to convince most people of the truth",
        89: "Sometimes I put off until tomorrow what I should do today",
        90: "I don't mind people making fun of me",
        91: "I would like to be a nurse",
        92: "I think most people would lie to get ahead",
        93: "I have often regretted things I have done",
        94: "I go to church (or temple) almost every week",
        95: "I have hardly ever quarreled with my family",
        96: "Sometimes I have a strong urge to do something shocking or harmful",
        97: "I believe in reward for good and punishment for evil",
        98: "I like to go to lively parties",
        99: "I run into problems that are so tangled I feel uncertain what to do",
        100: "I think women should have the same sexual freedom as men",
        101: "The hardest thing for me is controlling myself",
        102: "I rarely have muscle cramps or twitching",
        103: "I seem not to care about anything",
        104: "When I don't feel well I sometimes lose my temper",
        105: "I often feel as if I have done something wrong or committed a sin",
        106: "I am often happy",
        107: "I often feel as if my head is stuffed or my nose blocked",
        108: "Some people are so bossy that I do the opposite even when I know they are right",
    }
    if idx in t:
        return t[idx]
    # Placeholder for remaining items (can be replaced in mmpi_questions_en.txt and re-run add_question_english.py)
    return "Statement %d." % (idx + 1)

if __name__ == '__main__':
    main()
