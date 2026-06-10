import json

with open('C:/Users/Naxte/Downloads/ClaudeS/iSiTCompatible/app/src/main/assets/seed/guides/base-guides.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

for guide in data['guides']:
    if guide['id'] in ['base:aps3e:t4', 'base:cemu:t4']:
        print(f"\n{guide['id']}:")
        print(f"  Emulator: {guide.get('emulatorId')}")
        kinds = [s.get('kind') for s in guide.get('steps', [])]
        print(f"  Step kinds: {kinds}")
        for i, step in enumerate(guide.get('steps', [])):
            text = step.get('text', '')[:70]
            print(f"    {i}: {step.get('kind'):12} - {text}")
