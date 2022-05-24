package ru.spbstu.terrai.implementation

import ru.spbstu.terrai.core.*
import kotlin.math.sqrt


enum class State { JUST_EXPLORATION, ON_TRACE_TO_UNKNOWN_LOCATION, ON_TRACE_TO_FINISH }


class AdvancedPlayer : AbstractPlayer() {
    private var finishFound = false

    private var numberOfWormholes = 0

    /** <выходной Wormhole> to <координаты входа> **/
    private var outputWormholeToInputLocation = mutableMapOf<Wormhole, Location>()

    /** <выходной Wormhole> to <координаты выхода> **/
    private var outputWormholeToOutputLocation = mutableMapOf<Wormhole, Location>()

    /** <выходной Wormhole> to <условный номер> **/
    private var outputWormholeToId = mutableMapOf<Wormhole, Int>()

    private val traceQueue = mutableListOf<Move>()
    private var currentState = State.JUST_EXPLORATION
    private val roomMap = mutableMapOf<Location, Room>()
    private var lastMove: Move = WaitMove
    private var treasureFound = false
    private lateinit var traceToFinish: List<Move>;
    private lateinit var currLocation: Location
    private val unknownAvailableSet = mutableSetOf<Location>()

    private fun getFinishLocation(): Location {
        assert(finishFound)
        for ((location, room) in roomMap) {
            if(room is Exit) return location
        }
        error("finish wasn't found")
    }


    override fun setStartLocationAndSize(location: Location, width: Int, height: Int) {
        super.setStartLocationAndSize(location, width, height)
        currLocation = location


        roomMap[currLocation] = Entrance
        updateUnknownAvailableRooms(currLocation)
    }


    /*Это новый метод, поэтому его нет в отчёте.*/
    private fun checkTraceToFinish(): Boolean {
        val trace = findTraces(currLocation)[getFinishLocation()];
        return if (trace != null) {
            traceToFinish = trace;
            true;
        } else {
            false;
        }
    }

    override fun getNextMove(): Move {
        when (currentState) {
            State.JUST_EXPLORATION -> {
                //определяем направление к неизвестной ячейке
                val nextUnknown =
                    Direction.values().filter { it + currLocation !in roomMap }
                if (treasureFound && finishFound && checkTraceToFinish()) {
                    currentState = State.ON_TRACE_TO_FINISH
                    traceQueue.addAll(traceToFinish)
                    return getNextMove()
                } else if (nextUnknown.isNotEmpty()) {
                    val direction =
                        nextUnknown.minByOrNull {//такой выбор направления обеспечивает большую сжатость исследованной области
                            (it + currLocation).getAverageDistance(
                                unknownAvailableSet.filter
                                { location ->
                                    (location.x in currLocation.x - 500..currLocation.x + 500) &&
                                            (location.y in currLocation.y - 500..currLocation.y + 500)
                                }
                            )
                        }!!
                    this.lastMove = WalkMove(direction)
                    return this.lastMove
                } else {
                    return if (unknownAvailableSet.isNotEmpty()) {
                        currentState = State.ON_TRACE_TO_UNKNOWN_LOCATION
                        traceQueue.addAll(findBestTraceToUnknownRoom()!!)
                        getNextMove()
                    } else {
                        error("there is no treasure anywhere!")
                    }
                }

            }
            State.ON_TRACE_TO_UNKNOWN_LOCATION -> {
                return if (traceQueue.isNotEmpty()) {
                    this.lastMove = traceQueue.removeFirst()
                    this.lastMove
                } else {
                    currentState = State.JUST_EXPLORATION
                    getNextMove()
                }
            }

            State.ON_TRACE_TO_FINISH -> {
                this.lastMove = traceQueue.removeFirst()
                return this.lastMove
            }
        }

    }

    private fun updateUnknownAvailableRooms(location: Location) {
        val unknownNeighbors = Direction.values().map { it + location }.filter { !roomMap.containsKey(it) }
        val visitedNeighbors = Direction.values().map { it + location }.filter { roomMap.containsKey(it) }
        unknownAvailableSet.addAll(unknownNeighbors)
        unknownAvailableSet.removeAll(visitedNeighbors.toSet())
    }

    override fun setMoveResult(result: MoveResult) {
        val newLocation = (lastMove as? WalkMove)?.let { it.direction + currLocation } ?: currLocation
        val room = result.room
        roomMap[newLocation] = room
        if (result.successful) {
            when (room) {
                is Wormhole -> {
                    if (outputWormholeToOutputLocation.containsKey(room)) {
                        updateMapByWormhole(newLocation, room)
                        currLocation = outputWormholeToOutputLocation[room]!!
                    } else {
                        numberOfWormholes++
                        currLocation = Location(numberOfWormholes * 1000, numberOfWormholes * 1000)
                        outputWormholeToId[room] = numberOfWormholes
                        outputWormholeToOutputLocation[room] = currLocation
                        outputWormholeToInputLocation[room] = newLocation
                    }
                    //оставляем здесь выходной wormhole
                }
                is WithContent -> {
                    if (!treasureFound && result.condition.hasTreasure) {
                        treasureFound = true
                        currLocation = newLocation
                        ///roomMap[currentLocation] = room
                    }
                }
                is Exit -> {
                    finishFound = true
                    currLocation = newLocation
                    //roomMap[currentLocation] = room
                }
                else -> {
                    currLocation = newLocation
                }
            }
        } else { //стена
        }
        updateUnknownAvailableRooms(currLocation)
    }

    //возвращает маршрут к соседу неисследованной ячейки
    private fun findBestTraceToUnknownRoom(): List<Move>? {
        val traces = findTraces(currLocation)
        val tracesSet = mutableSetOf<List<Move>>()
        unknownAvailableSet.forEach { unknownLocation ->
            val bestNeighbourToDirection = Direction.values().map { it + unknownLocation to it.turnBack() }
                .filter {
                    it.first in traces.keys
                }.minByOrNull { traces[it.first]!!.size }
            if (bestNeighbourToDirection != null) {
                tracesSet.add(traces[bestNeighbourToDirection.first]!!)
            }
        }
        return tracesSet.minByOrNull { it.size }
    }

    //если был найден wormHole, который ранее уже встречался, то стоит объединить исследованную область
    private fun updateMapByWormhole(inputLocation: Location, wormhole: Wormhole) {
        val oldLocation = outputWormholeToInputLocation[wormhole]!!

        if (oldLocation != inputLocation) {
            val deltaX = oldLocation.x - inputLocation.x
            val deltaY = oldLocation.y - inputLocation.y

            val oldRoomSubMap = roomMap.filter {
                (it.key.x in (inputLocation.x - 500)..(inputLocation.x + 500)) &&
                        (it.key.y in (inputLocation.y - 500)..(inputLocation.y + 500))
            }

            oldRoomSubMap.forEach {
                roomMap.remove(it.key)
                val newLocation = Location(it.key.x + deltaX, it.key.y + deltaY)
                if (roomMap[newLocation] == null) {
                    roomMap[newLocation] = it.value
                } else if (roomMap[newLocation] != it.value) {
                    error("Wrong labyrinth!")
                }
            }
            outputWormholeToOutputLocation.update(inputLocation, deltaX, deltaY)
            outputWormholeToInputLocation.update(inputLocation, deltaX, deltaY)
        }
    }

    private fun MutableMap<Wormhole, Location>.update(inputLocation: Location, deltaX: Int, deltaY: Int) {
        val newMap = mutableMapOf<Wormhole, Location>()
        this.forEach {
            if ((it.value.x in (inputLocation.x - 500)..(inputLocation.x + 500)) &&
                (it.value.y in (inputLocation.y - 500)..(inputLocation.y + 500))
            ) {
                newMap[it.key] = Location(it.value.x + deltaX, it.value.y + deltaY)
            }
        }
        newMap.forEach { this[it.key] = it.value }
    }


    //поиск наилучших маршрутов по исследованным ячейкам
    private fun findTraces(sourceLocation: Location): Map<Location, List<Move>> {
        val mapOfTraces = mutableMapOf<Location, List<Move>>()
        var setOfCurrentLocations = setOf(sourceLocation)
        val wormholesOutputToInputMap = mutableMapOf<Location, Location>()
        while (setOfCurrentLocations.isNotEmpty()) {
            val newSetOfLocation = mutableSetOf<Location>()
            setOfCurrentLocations.forEach { currentLocation ->
                //если текущая позиция является точкой выхода из кротовой норы,
                // то у текущей ячейки источниками могут быть только соседи входной точки в кротовую нору
                val neighbours = Direction.values().map { it + currentLocation }
                val sourceNeighboursToDirection =
                    if (wormholesOutputToInputMap.containsKey(currentLocation)) {
                        Direction.values().map { it + wormholesOutputToInputMap[currentLocation]!! to it.turnBack() }
                    } else {
                        Direction.values().map { it + currentLocation to it.turnBack() }
                    }

                //следующей ячейкой может быть любая,
                //если это вход в кротовую нору то мы рассматриваем в качестве следующей точку выхода
                newSetOfLocation.addAll(neighbours.filter {
                    roomMap[it] is Empty || roomMap[it] is WithContent || roomMap[it] is Entrance
                            || roomMap[it] is Exit || roomMap[it] is Wormhole
                }.map {
                    if (roomMap[it] is Wormhole) {
                        val output = outputWormholeToOutputLocation[roomMap[it]]!!
                        val input = it
                        wormholesOutputToInputMap[output] = input
                        output
                    } else {
                        it
                    }
                }
                )
                val bestNeighbourToDirection =
                    sourceNeighboursToDirection.filter { mapOfTraces.containsKey(it.first) }
                        .minByOrNull { mapOfTraces[it.first]!!.size }
                if (bestNeighbourToDirection == null) { //если это первая ячейка
                    mapOfTraces[currentLocation] = listOf()
                } else {
                    val newTrace =
                        mapOfTraces[bestNeighbourToDirection.first]!! + WalkMove(bestNeighbourToDirection.second)
                    mapOfTraces[currentLocation] = newTrace
                }
            }
            setOfCurrentLocations = newSetOfLocation.filter {
                !mapOfTraces.containsKey(it) || (roomMap[it] is Wormhole && !mapOfTraces.containsKey(
                    outputWormholeToOutputLocation[roomMap[it]]
                ))
            }.toSet()
        }
        return mapOfTraces
    }
}

fun Location.getDistance(other: Location): Double {
    val a = this.x - other.x
    val b = this.y - other.y
    return sqrt((a * a + b * b).toDouble())
}

fun Location.getAverageDistance(listLocations: Iterable<Location>): Double {
    return listLocations.map { it.getDistance(this) }.average()
}



