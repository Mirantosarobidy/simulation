# Simulation Multi-Cloud — Comparaison 3 Stratégies

## Description

Ce projet implémente et compare trois stratégies de réplication de données en environnement multi-cloud (CloudSim), conformément à l'article TCDRM (JLISS 2025).

| Stratégie      | Description |
|----------------|-------------|
| **NoRepLc**    | Baseline : sélection du provider le moins cher, aucune réplication |
| **TCDRM**      | Heuristique de réplication tenant compte du budget, SLA et popularité |
| **TCDRM-Pred** | TCDRM + prédiction de popularité (P1: EMA/Régression/LSTM) + placement Pareto multi-objectifs (P2: coût, latence, BW, sync) + modèle de cohérence (P3: Critical/ReadMostly/Eventual) |

## Lancement

### Point d'entrée principal (comparaison 3 stratégies)
```
mvn package -q
java -jar target/simulation-comparison.jar
```
Ou depuis l'IDE : exécuter `MainComparison.main()`

### Autres points d'entrée disponibles
- `Main` : NoRepLc seul
- `MainTCDRM` : NoRepLc + TCDRM (simulation CloudSim complète à 2 passes)
- `MainTCDRMPred` : NoRepLc + TCDRM-Pred (simulation CloudSim complète)

## Fichiers produits
- `simulation_comparison.log` : logs détaillés de toutes les passes
- 8 fenêtres graphiques (XChart) : temps de réponse, coûts BW, coûts cumulés, SLA violations, réplicas, méthodes prédiction

## Indicateurs mesurés
1. Violations SLA (latence et coût) par période
2. Latence moyenne, p95, p99
3. Coût bande passante inter-cloud (volume et coût)
4. Coût total cumulé (amortissement économique)
5. Facteur de réplication (créations et suppressions)
6. Stabilité des décisions (taux de thrashing)
7. Répartition des méthodes de prédiction (EMA/Régression/LSTM)
8. Budget restant (TCDRM-Pred)

## Architecture
```
MainComparison
├── Passe 0 : NoRepLc (CloudSim)     → SimulationMetrics norep
├── Passe 1 : TCDRM (sur métriques)  → SimulationMetrics tcdrm
├── Passe 2 : TCDRM-Pred (idem)      → SimulationMetrics pred
├── MetricsCollector.printComparativeReport()
└── DisplayChart.plotAllComparisons() → 8 graphiques
```
